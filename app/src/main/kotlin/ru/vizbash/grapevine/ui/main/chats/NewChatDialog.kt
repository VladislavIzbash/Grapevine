package ru.vizbash.grapevine.ui.main.chats

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import ru.vizbash.grapevine.R
import ru.vizbash.grapevine.databinding.DialogNewChatBinding
import ru.vizbash.grapevine.ui.main.MainViewModel
import ru.vizbash.grapevine.util.validateName
import javax.inject.Inject

@AndroidEntryPoint
class NewChatDialog : BottomSheetDialogFragment() {
    @Inject lateinit var imagePicker: ImagePicker.Builder

    companion object {
        private const val TAG = "NewChatDialog"
    }

    private lateinit var ui: DialogNewChatBinding
    private val activityModel: MainViewModel by activityViewModels()

    private lateinit var pickerLauncher: ActivityResultLauncher<Intent>

    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pickerLauncher = registerForActivityResult(StartActivityForResult()) { res ->
            when (res.resultCode) {
                Activity.RESULT_OK -> {
                    photoUri = res.data!!.data!!
                    ui.photo.setImageURI(photoUri)
                }
                ImagePicker.RESULT_ERROR -> Log.e(TAG, ImagePicker.getError(res.data))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ui = DialogNewChatBinding.inflate(inflater, container, false)

        ui.photo.setOnClickListener {
            imagePicker.createIntent { pickerLauncher.launch(it) }
        }

        ui.createButton.setOnClickListener {
            val name = ui.nameField.text.toString()

            if (validateName(name)) {
                activityModel.createGroupChat(ui.nameField.text.toString(), photoUri)
                dismiss()
            } else {
                Snackbar.make(ui.root, R.string.invalid_name, Snackbar.LENGTH_SHORT)
                    .setAnchorView(ui.root)
                    .show()
            }
        }

        return ui.root
    }
}