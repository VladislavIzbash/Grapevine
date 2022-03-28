package ru.vizbash.grapevine.ui.newprofile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.databinding.ActivityNewProfileBinding
import ru.vizbash.grapevine.ui.main.MainActivity
import ru.vizbash.grapevine.ui.newprofile.NewProfileModel.CreationState.*
import javax.inject.Inject

@AndroidEntryPoint
class NewProfileActivity : AppCompatActivity() {
    @Inject lateinit var imagePicker: ImagePicker.Builder

    private lateinit var ui: ActivityNewProfileBinding
    private val model: NewProfileModel by viewModels()

    companion object {
        private const val TAG = "NewProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ActivityNewProfileBinding.inflate(layoutInflater)
        setContentView(ui.root)

        initUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.validation.collect { v ->
                        ui.nameFieldLayout.error = v.nameError?.let { getString(it) }
                        ui.passwordFieldLayout.error = v.passwordError?.let { getString(it) }
                        ui.passwordRepeatFieldLayout.error = v.passwordRepeatError?.let { getString(it) }
                    }
                }
                launch {
                    model.creationState.collect(::applyState)
                }
            }
        }
    }

    private fun initUi() {
        ui.nameField.setText(model.form.value.username)
        ui.passwordField.setText(model.form.value.password)
        ui.passwordRepeatField.setText(model.form.value.passwordRepeat)
        ui.autoLoginCheck.isChecked = model.form.value.autoLogin
        model.form.value.photoUri?.let {
            ui.photo.setImageURI(it)
        }

        ui.nameField.addTextChangedListener {
            model.form.value = model.form.value.copy(username = it.toString())
        }
        ui.passwordField.addTextChangedListener {
            model.form.value = model.form.value.copy(password = it.toString())
        }
        ui.passwordRepeatField.addTextChangedListener {
            model.form.value = model.form.value.copy(passwordRepeat = it.toString())
        }
        ui.autoLoginCheck.setOnCheckedChangeListener { _, checked ->
            model.form.value = model.form.value.copy(autoLogin = checked)
        }

        val pickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            when (res.resultCode) {
                Activity.RESULT_OK -> {
                    model.form.value = model.form.value.copy(
                        photoUri = res.data!!.data!!,
                    )
                    ui.photo.setImageURI(model.form.value.photoUri)
                }
                ImagePicker.RESULT_ERROR -> Log.e(TAG, ImagePicker.getError(res.data))
            }
        }

        ui.photo.setOnClickListener {
            imagePicker.createIntent { pickerLauncher.launch(it) }
        }

        ui.createProfileButton.setOnClickListener {
            model.createProfile()
        }
    }

    private fun applyState(state: NewProfileModel.CreationState) = when (state) {
        INVALID -> ui.createProfileButton.isEnabled = false
        VALID -> ui.createProfileButton.isEnabled = true
        CREATING -> ui.creatingProgress.visibility = View.VISIBLE
        CREATED -> {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}