package ru.vizbash.grapevine.ui

import android.app.Activity
import android.content.Context
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
class UiModule {
    @Provides
    @ActivityScoped
    fun provideImagePicker(@ActivityContext context: Context): ImagePicker.Builder {
        return ImagePicker.with(context as Activity)
            .compress(256)
            .maxResultSize(256, 256)
            .cropSquare()
    }

}