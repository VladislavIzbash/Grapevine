package ru.vizbash.grapevine.ui.newprofile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.util.validateName

class NewProfileModel : ViewModel() {
    enum class CreationState { INVALID, VALID, LOADING, CREATED }

    data class FormState(
        val name: String = "",
        val password: String = "",
        val passwordRepeat: String = "",
        val photoUri: Uri? = null,
    )

    data class Validation(
        val nameError: Int? = null,
        val passwordError: Int? = null,
        val passwordRepeatError: Int? = null,
    )

    private val _creationState = MutableStateFlow(CreationState.INVALID)
    val creationState = _creationState.asStateFlow()

    val form = MutableStateFlow(FormState())
    private val _validation = MutableStateFlow(Validation())
    val validation = _validation.asStateFlow()

    init {
        viewModelScope.launch {
            form.collect(::validateForm)
        }
    }

    private fun validateForm(form: FormState) {
        val nameValid = validateName(form.name)
        val passwordNotBlank = form.password.isNotBlank()
        val passwordIsLong = form.password.length >= 8
        val passwordsMatch = form.password == form.passwordRepeat

        _validation.value = Validation(
            nameError = if (!nameValid && form.name.isNotEmpty()) {
                R.string.invalid_username
            } else {
                null
            },
            passwordError = when {
                !passwordNotBlank && form.password.isNotEmpty() -> R.string.password_is_blank
                !passwordIsLong && form.password.isNotEmpty()  -> R.string.password_too_short
                else -> null
            },
            passwordRepeatError = if (!passwordsMatch && form.passwordRepeat.isNotEmpty()) {
                R.string.passwords_dont_match
            } else {
                null
            },
        )

        val allValid = nameValid && passwordNotBlank && passwordIsLong && passwordsMatch
        _creationState.value = if (allValid) CreationState.VALID else CreationState.INVALID
    }
}