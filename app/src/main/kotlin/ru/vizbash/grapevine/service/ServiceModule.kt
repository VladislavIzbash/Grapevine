package ru.vizbash.grapevine.service

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.vizbash.grapevine.service.profile.ProfileProvider
import ru.vizbash.grapevine.service.profile.ProfileService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    companion object {
        @ServiceCoroutineScope
        @Singleton
        @Provides
        fun provideServiceCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default)
    }

    @Binds
    abstract fun bindNodeVerifier(profileService: ProfileService): NodeVerifier

    @Binds
    abstract fun bindProfileProvider(profileService: ProfileService): ProfileProvider
}