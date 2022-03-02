package ru.vizbash.grapevine

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.vizbash.grapevine.service.NodeVerifier
import ru.vizbash.grapevine.service.ProfileProvider
import ru.vizbash.grapevine.service.ProfileService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindProfileProvider(impl: ProfileService): ProfileProvider

    @Binds
    @Singleton
    abstract fun bindNodeValidator(impl: ProfileService): NodeVerifier
}