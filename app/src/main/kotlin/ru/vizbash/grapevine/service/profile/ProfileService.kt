package ru.vizbash.grapevine.service.profile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.protobuf.ByteString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.service.NodeVerifier
import ru.vizbash.grapevine.storage.Profile.StoredProfile
import ru.vizbash.grapevine.storage.node.KnownNode
import ru.vizbash.grapevine.storage.node.NodeDao
import ru.vizbash.grapevine.storage.storedProfile
import ru.vizbash.grapevine.util.*
import java.io.IOException
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ProfileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nodeDao: NodeDao,
) : ProfileProvider, NodeVerifier {

    companion object {
        private const val TAG = "ProfileService"
        private const val AUTOLOGIN_PASSWORD_KEY = "autologin_password"
        private const val AUTOLOGIN_KEY = "autologin"
    }

    private val _profileFlow = MutableStateFlow<Profile?>(null)
    val profileFlow = _profileFlow.asStateFlow()

    override val profile get() = profileFlow.value!!

    private var storedProfile: StoredProfile? = null

    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)

    private lateinit var password: String

    val hasProfile get() = storedProfile != null

    val storedName: String
        get() = storedProfile!!.username

    init {
        try {
            context.openFileInput("profile").use {
                storedProfile = StoredProfile.parseFrom(it.readBytes())
            }
            Log.i(TAG, "Loaded profile ${storedProfile!!.username}")
        } catch (e: IOException) {
        }
    }

    suspend fun tryLogin(
        password: String,
        autoLogin: Boolean,
    ): Boolean = withContext(Dispatchers.Default) {
        val storedProfile = requireNotNull(storedProfile)

        val secret = generatePasswordSecret(password)
        val privKey = aesDecrypt(storedProfile.privateKeyEnc.toByteArray(), secret)
            ?: return@withContext false

        this@ProfileService.password = password

        val sessionKeys = generateSessionKeys()

        _profileFlow.value = Profile(
            nodeId = storedProfile.nodeId,
            username = storedProfile.username,
            pubKey = decodeRsaPublicKey(storedProfile.publicKey.toByteArray()),
            privKey = decodeRsaPrivateKey(privKey),
            sessionPubKey = sessionKeys.public as DHPublicKey,
            sessionPrivKey = sessionKeys.private as DHPrivateKey,
            photo = if (storedProfile.hasPhoto) {
                decodeBitmap(storedProfile.photo.toByteArray())
            } else {
                null
            },
        )

        Log.i(TAG, "Decrypted profile ${profile.username}")

        saveAutoLogin(autoLogin, password)

        true
    }

    suspend fun tryAutoLogin(): Boolean {
        if (!sharedPrefs.getBoolean(AUTOLOGIN_KEY, false)) {
            return false
        }
        this.password = sharedPrefs.getString(AUTOLOGIN_PASSWORD_KEY, null)
            ?: return false

        return tryLogin(password, true)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun loginWithNewProfile(
        username: String,
        password: String,
        photoUri: Uri?,
        autoLogin: Boolean,
    ) {
        val photo = photoUri?.let {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(it)
                BitmapFactory.decodeStream(input)
            }
        }

        withContext(Dispatchers.Default) {
            val keyPair = generateRsaKeys()
            val sessionKeyPair = generateSessionKeys()

            _profileFlow.value = Profile(
                nodeId = Random.nextLong(),
                username = username,
                pubKey = keyPair.public,
                privKey = keyPair.private,
                sessionPubKey = sessionKeyPair.public as DHPublicKey,
                sessionPrivKey = sessionKeyPair.private as DHPrivateKey,
                photo = photo,
            )
            saveProfile()
        }

        nodeDao.insert(profile.toKnownNode())

        saveAutoLogin(autoLogin, password)
    }

    suspend fun editProfile(profile: Profile) {
        _profileFlow.value = profile
        saveProfile()
    }

    private fun saveAutoLogin(autoLogin: Boolean, password: String) {
        if (autoLogin) {
            sharedPrefs.edit()
                .putBoolean(AUTOLOGIN_KEY, true)
                .putString(AUTOLOGIN_PASSWORD_KEY, password)
                .apply()
        } else {
            disableAutoLogin()
        }
    }

    fun disableAutoLogin() {
        sharedPrefs.edit()
            .remove(AUTOLOGIN_PASSWORD_KEY)
            .putBoolean(AUTOLOGIN_KEY, false)
            .apply()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun saveProfile() {
        val privKey = aesEncrypt(profile.privKey.encoded, generatePasswordSecret(password))

        storedProfile = storedProfile {
            nodeId = profile.nodeId
            username = profile.username
            publicKey = ByteString.copyFrom(profile.pubKey.encoded)
            privateKeyEnc = ByteString.copyFrom(privKey)
            hasPhoto = profile.photo != null
            profile.photo?.let {
                photo = ByteString.copyFrom(encodeBitmap(it))
            }
        }

        withContext(Dispatchers.IO) {
            val output = context.openFileOutput("profile", Context.MODE_PRIVATE)
            storedProfile!!.writeTo(output)
        }
    }

    override suspend fun checkNode(node: Node): Boolean {
        val knownNode = nodeDao.getById(node.id) ?: return true
        return knownNode.pubKey == node.pubKey
    }
}