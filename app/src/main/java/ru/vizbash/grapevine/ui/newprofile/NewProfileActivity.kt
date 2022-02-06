package ru.vizbash.grapevine.ui.newprofile

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.esafirm.imagepicker.features.ImagePickerConfig
import com.esafirm.imagepicker.features.ImagePickerMode
import com.esafirm.imagepicker.features.ReturnMode
import com.esafirm.imagepicker.features.registerImagePicker
import com.esafirm.imagepicker.model.Image
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityNewIdentityBinding
import ru.vizbash.grapevine.ui.MainActivity

@AndroidEntryPoint
class NewProfileActivity : AppCompatActivity() {
    private lateinit var ui: ActivityNewIdentityBinding
    private val model: NewProfileModel by viewModels()

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityNewIdentityBinding.inflate(layoutInflater)

        val pickerConfig = ImagePickerConfig {
            theme = R.style.Theme_Grapevine_ImagePicker
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

        ui.buttonCreateIdentity.setOnClickListener { onCreateClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.creationState.collect { state ->
                    when (state) {
                        NewProfileModel.CreationState.NONE -> {
                            ui.buttonCreateIdentity.isEnabled = true
                            ui.progCreation.visibility = View.INVISIBLE
                        }
                        NewProfileModel.CreationState.LOADING -> {
                            ui.buttonCreateIdentity.isEnabled = false
                            ui.progCreation.visibility = View.VISIBLE
                        }
                        NewProfileModel.CreationState.CREATED -> {
                            ui.progCreation.visibility = View.INVISIBLE

                            val intent = Intent(
                                this@NewProfileActivity,
                                MainActivity::class.java,
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
        }

        setContentView(ui.root)
    }

    private fun onCreateClicked() {
        val photo = photoUri?.let {
            val input = contentResolver.openInputStream(it)
            BitmapFactory.decodeStream(input)
        }

        model.createProfileAndLogin(
            ui.newEditUsername.text.toString(),
            ui.newEditPassword.text.toString(),
            photo,
        )
    }

    private fun onPhotoPicked(images: List<Image>) {
        ui.ivPhoto.setImageURI(images[0].uri)
        photoUri = images[0].uri
    }

    private inner class ValidatingWatcher(private val editText: EditText) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (model.creationState.value != NewProfileModel.CreationState.NONE) {
                return
            }

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