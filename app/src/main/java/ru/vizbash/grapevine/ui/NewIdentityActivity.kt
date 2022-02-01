package ru.vizbash.grapevine.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.esafirm.imagepicker.features.ImagePickerConfig
import com.esafirm.imagepicker.features.ImagePickerMode
import com.esafirm.imagepicker.features.ReturnMode
import com.esafirm.imagepicker.features.registerImagePicker
import com.esafirm.imagepicker.model.Image
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import ru.vizbash.grapevine.AuthService
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityNewIdentityBinding
import javax.inject.Inject

@AndroidEntryPoint
class NewIdentityActivity : AppCompatActivity() {
    @Inject lateinit var authService: AuthService

    private lateinit var ui: ActivityNewIdentityBinding

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityNewIdentityBinding.inflate(layoutInflater)

        val pickerConfig = ImagePickerConfig {
            theme = R.style.Theme_ImagePicker
            mode = ImagePickerMode.SINGLE
            language = "ru"
            returnMode = ReturnMode.ALL
        }

        val pickerLauncher = registerImagePicker(this::onPhotoPicked)

        ui.ivPhoto.setOnClickListener {
            pickerLauncher.launch(pickerConfig)
        }

        ui.newEditUsername.addTextChangedListener(ValidatingWatcher(ui.newEditUsername))
        ui.newEditPassword.addTextChangedListener(ValidatingWatcher(ui.newEditPassword))
        ui.newEditPasswordRepeat.addTextChangedListener(ValidatingWatcher(ui.newEditPasswordRepeat))

        ui.buttonCreateIdentity.setOnClickListener(this::onCreateClicked)

        setContentView(ui.root)
    }

    private fun onCreateClicked(v: View) {
        ui.progCreation.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val photo = photoUri?.let {
                val input = contentResolver.openInputStream(it)
                BitmapFactory.decodeStream(input)
            }

            authService.newIdentity(
                ui.newEditUsername.text.toString(),
                ui.newEditPassword.text.toString(),
                photo,
            )

            withContext(Dispatchers.Main) {
                val prefs = getSharedPreferences(getString(R.string.login_prefs), MODE_PRIVATE)
                with(prefs.edit()) {
                    putString(getString(R.string.prefs_last_username), ui.newEditUsername.text.toString())
                    apply()
                }

                finish()
            }
        }
    }

    private fun onPhotoPicked(images: List<Image>) {
        ui.ivPhoto.setImageURI(images[0].uri)
        photoUri = images[0].uri
    }

    private inner class ValidatingWatcher(private val editText: EditText) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            val usernameValid =
                ui.newEditUsername.text!!.matches(Regex("[а-яА-Яa-zA-Z0-9_ ]{4,}"))

            val passwordNotBlank = ui.newEditPassword.text!!.isNotBlank()
            val passwordIsLong = ui.newEditPassword.text!!.length >= 8
            val passwordsMatch =
                ui.newEditPassword.text.toString() == ui.newEditPasswordRepeat.text.toString()

            when (editText) {
                ui.newEditUsername -> {
                    ui.newLayoutUsername.error = if (!usernameValid) {
                        getString(R.string.error_invalid_username)
                    } else {
                        null
                    }
                }
                ui.newEditPassword -> {
                    ui.newLayoutPassword.error = if (!passwordNotBlank) {
                        getString(R.string.password_is_blank)
                    } else if (!passwordIsLong) {
                        getString(R.string.password_too_short)
                    } else {
                        null
                    }
                }
                ui.newEditPasswordRepeat -> {
                    ui.newLayoutPasswordRepeat.error = if (!passwordsMatch) {
                        getString(R.string.passwords_dont_match)
                    } else {
                        null
                    }
                }
            }

            ui.buttonCreateIdentity.isEnabled = usernameValid
                    && passwordNotBlank
                    && passwordIsLong
                    && passwordsMatch
        }
    }
}