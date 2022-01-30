package ru.vizbash.grapevine.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import java.security.KeyPairGenerator

@Module
@InstallIn(ServiceComponent::class)
class NetworkModule {
    @Provides
    fun provideRouter(): Router {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(1024)
        return Router(Node(1, "Test", keyGen.genKeyPair().public))
    }
}