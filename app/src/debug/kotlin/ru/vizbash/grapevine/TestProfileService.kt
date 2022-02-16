package ru.vizbash.grapevine

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import ru.vizbash.grapevine.storage.profile.ProfileEntity

class TestProfileService(profile: Profile) : IProfileService {
    override val currentProfile = profile

    override val profileList: Flow<List<ProfileEntity>>
        get() = throw NotImplementedError()


    override suspend fun createProfileAndLogin(username: String, password: String, photo: Bitmap?) {
        throw NotImplementedError()
    }

    override suspend fun tryLogin(profile: ProfileEntity, password: String): Boolean {
        throw NotImplementedError()
    }
}