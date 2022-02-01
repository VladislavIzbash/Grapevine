package ru.vizbash.grapevine.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.vizbash.grapevine.db.identity.IdentityDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DbModule {
    @Singleton
    @Provides
    fun provideIdentityDb(@ApplicationContext context: Context) = Room.databaseBuilder(
        context,
        IdentityDatabase::class.java,
        "identities",
    ).allowMainThreadQueries().build()

    @Singleton
    @Provides
    fun provideIdentityDao(db: IdentityDatabase) = db.identityDao()
}