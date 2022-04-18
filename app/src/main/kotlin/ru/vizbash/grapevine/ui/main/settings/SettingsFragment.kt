package ru.vizbash.grapevine.ui.main.settings

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.FragmentSettingsBinding
import ru.vizbash.grapevine.service.profile.ProfileService
import ru.vizbash.grapevine.ui.newprofile.NewProfileActivity
import ru.vizbash.grapevine.util.validateName
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    @Inject lateinit var profileService: ProfileService
    @Inject lateinit var imagePicker: ImagePicker.Builder

    private var _ui: FragmentSettingsBinding? = null
    private val ui get() = _ui!!

    private var usernameEdited = false

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _ui = FragmentSettingsBinding.inflate(layoutInflater, container, false)

        ui.username.isEnabled = false
        ui.editButton.setOnClickListener {
            usernameEdited = !usernameEdited
            ui.username.isEnabled = usernameEdited

            if (usernameEdited) {
                ui.username.requestFocus()
                requireContext().getSystemService(InputMethodManager::class.java).showSoftInput(ui.username, InputMethodManager.SHOW_IMPLICIT)
                ui.editButton.setImageResource(R.drawable.ic_check)
            } else {
                ui.username.clearFocus()
                ui.editButton.setImageResource(R.drawable.ic_edit)

                lifecycleScope.launch {
                    profileService.editProfile(profileService.profile.copy(
                        username = ui.username.text.toString(),
                    ))
                }
            }
        }

        ui.username.addTextChangedListener {
            if (it!!.isNotEmpty() && !validateName(it.toString())) {
                ui.username.error = getString(R.string.invalid_name)
                ui.editButton.isEnabled = false
            } else {
                ui.editButton.isEnabled = true
            }
        }

        if (profileService.profile.photo != null) {
            ui.photo.setImageBitmap(profileService.profile.photo)
        }
        ui.username.setText(profileService.profile.username)

        val pickerLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            when (res.resultCode) {
                Activity.RESULT_OK -> {
                    val uri = res.data!!.data!!
                    ui.photo.setImageURI(uri)

                    lifecycleScope.launch(Dispatchers.IO) {
                        val input = requireContext().contentResolver.openInputStream(uri)
                        val photo = BitmapFactory.decodeStream(input)

                        profileService.editProfile(profileService.profile.copy(
                            photo = photo,
                        ))
                    }
                }
                else -> {}
            }
        }

        ui.photo.setOnClickListener {
            imagePicker.createIntent { pickerLauncher.launch(it) }
        }

        return ui.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _ui = null
    }
}