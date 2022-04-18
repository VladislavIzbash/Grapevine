package ru.vizbash.grapevine.ui.main.settings

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import ru.vizbash.grapevine.R

class SettingsPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        findPreference<EditTextPreference>("max_connections")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }
        findPreference<EditTextPreference>("file_block_size")?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }


    }
}