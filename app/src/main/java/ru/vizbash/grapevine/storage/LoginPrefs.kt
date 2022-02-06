package ru.vizbash.grapevine.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("login", Context.MODE_PRIVATE)

    var lastUsername
        get() = prefs.getString("last_username", null)
        set(value) = prefs.edit().putString("last_username", value).apply()

    var autoLoginUsername
        get() = prefs.getString("autologin_username", null)
        set(value) = prefs.edit().putString("autologin_username", value).apply()

    var autoLoginPassword
        get() = prefs.getString("autologin_password", null)
        set(value) = prefs.edit().putString("autologin_password", value).apply()
}