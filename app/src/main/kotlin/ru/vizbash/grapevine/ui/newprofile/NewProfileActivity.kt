package ru.vizbash.grapevine.ui.newprofile

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.dhaval2404.imagepicker.ImagePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.ActivityNewProfileBinding
import ru.vizbash.grapevine.ui.newprofile.NewProfileModel.CreationState.*

@AndroidEntryPoint
class NewProfileActivity : AppCompatActivity() {
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
                    model.creationState.collect { state ->
                        when (state) {
                            INVALID -> ui.createProfile.isEnabled = false
                            VALID -> ui.createProfile.isEnabled = true
                            LOADING -> ui.creatingProgress.visibility = View.VISIBLE
                            CREATED -> TODO()
                        }
                    }
                }
            }
        }
    }

    private fun initUi() {
        ui.nameField.setText(model.form.value.name)
        ui.passwordField.setText(model.form.value.password)
        ui.passwordRepeatField.setText(model.form.value.passwordRepeat)
        model.form.value.photoUri?.let {
            ui.photo.setImageURI(it)
        }

        ui.nameField.addTextChangedListener(AfterTextWatcher {
            model.form.value = model.form.value.copy(
                name = it,
            )
        })
        ui.passwordField.addTextChangedListener(AfterTextWatcher {
            model.form.value = model.form.value.copy(
                password = it,
            )
        })
        ui.passwordRepeatField.addTextChangedListener(AfterTextWatcher {
            model.form.value = model.form.value.copy(
                passwordRepeat = it,
            )
        })

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
            ImagePicker.with(this)
                .compress(256)
                .maxResultSize(256, 256)
                .cropSquare()
                .createIntent {
                    pickerLauncher.launch(it)
                }
        }
    }

    private inner class AfterTextWatcher(
        private val afterTextChanged: (String) -> Unit,
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            afterTextChanged(s.toString())
        }
    }
}