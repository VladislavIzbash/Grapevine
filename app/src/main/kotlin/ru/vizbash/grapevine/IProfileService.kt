package ru.vizbash.grapevine

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import ru.vizbash.grapevine.storage.profile.ProfileEntity
import javax.inject.Singleton

interface IProfileService {
    val currentProfile: Profile

    val profileList: Flow<List<ProfileEntity>>

    suspend fun createProfileAndLogin(username: String, password: String, photo: Bitmap?)

    suspend fun tryLogin(profile: ProfileEntity, password: String): Boolean
}