package ru.vizbash.grapevine.storage

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.vizbash.grapevine.storage.chat.ChatDao
import ru.vizbash.grapevine.storage.message.MessageDao
import ru.vizbash.grapevine.storage.node.NodeDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class StorageModule {
    @Singleton
    @Provides
    fun provideProfileDatabase(@ApplicationContext context: Context): ProfileDatabase {
        return Room.databaseBuilder(
            context,
            ProfileDatabase::class.java,
            "profile",
        ).build()
    }

    @Singleton
    @Provides
    fun provideChatDao(db: ProfileDatabase): ChatDao = db.chatDao()

    @Singleton
    @Provides
    fun provideMessageDao(db: ProfileDatabase): MessageDao = db.messageDao()

    @Singleton
    @Provides
    fun provideNodeDao(db: ProfileDatabase): NodeDao = db.nodeDao()
}