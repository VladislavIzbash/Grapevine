package ru.vizbash.grapevine.network

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.vizbash.grapevine.network.dispatch.NetworkController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    companion object {
        @DispatcherCoroutineScope
        @Singleton
        @Provides
        fun provideDispatcherCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default)
    }

    @Binds
    abstract fun bindNodeProvider(grapevineNetwork: NetworkController): NodeProvider
}