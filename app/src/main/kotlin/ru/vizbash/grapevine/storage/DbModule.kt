package ru.vizbash.grapevine.storage

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.vizbash.grapevine.storage.profile.ProfileDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DbModule {
    @Singleton
    @Provides
    fun provideProfileDatabase(@ApplicationContext context: Context) = Room.databaseBuilder(
        context,
        ProfileDatabase::class.java,
        "profiles",
    ).build()

    @Singleton
    @Provides
    fun provideProfileDao(db: ProfileDatabase) = db.profileDao()
}