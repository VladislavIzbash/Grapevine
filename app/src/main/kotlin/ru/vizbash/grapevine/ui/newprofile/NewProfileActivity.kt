package ru.vizbash.grapevine.ui.newprofile

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.TAG
import ru.vizbash.grapevine.databinding.ActivityNewProfileBinding
import ru.vizbash.grapevine.ui.main.MainActivity

@AndroidEntryPoint
class NewProfileActivity : AppCompatActivity() {
    private lateinit var ui: ActivityNewProfileBinding
    private val model: NewProfileModel by viewModels()

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityNewProfileBinding.inflate(layoutInflater)
        setSupportActionBar(ui.toolbar)
        setContentView(ui.root)

        val pickerLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            when (res.resultCode) {
                Activity.RESULT_OK -> {
                    photoUri = res.data!!.data!!
                    ui.ivPhoto.setImageURI(photoUri)
                }
                ImagePicker.RESULT_ERROR -> Log.e(TAG, ImagePicker.getError(res.data))
            }
        }

        ui.ivPhoto.setOnClickListener {
            ImagePicker.with(this)
                .compress(256)
                .maxResultSize(128, 128)
                .cropSquare()
                .createIntent {
                    pickerLauncher.launch(it)
                }
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

                            finish()
                        }
                    }
                }
            }
        }
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