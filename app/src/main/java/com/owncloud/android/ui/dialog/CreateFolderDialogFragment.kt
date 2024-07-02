/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2018 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2015 David A. Velasco <dvelasco@solidgear.es>
 * SPDX-FileCopyrightText: 2015 ownCloud Inc.
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.Sets
import com.nextcloud.client.di.Injectable
import com.nextcloud.utils.extensions.getParcelableArgument
import com.nextcloud.utils.fileNameValidator.FileNameValidator
import com.owncloud.android.R
import com.owncloud.android.databinding.EditBoxDialogBinding
import com.owncloud.android.datamodel.FileDataStorageManager
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.ui.activity.ComponentsGetter
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.KeyboardUtils
import com.owncloud.android.utils.theme.ViewThemeUtils
import javax.inject.Inject

/**
 * Dialog to input the name for a new folder to create.
 *
 *
 * Triggers the folder creation when name is confirmed.
 */
class CreateFolderDialogFragment : DialogFragment(), DialogInterface.OnClickListener, Injectable {

    @Inject
    lateinit var fileDataStorageManager: FileDataStorageManager

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var keyboardUtils: KeyboardUtils

    private var mParentFolder: OCFile? = null
    private var positiveButton: MaterialButton? = null

    private lateinit var binding: EditBoxDialogBinding

    override fun onStart() {
        super.onStart()
        bindButton()
    }

    private fun bindButton() {
        val dialog = dialog

        if (dialog is AlertDialog) {
            positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE) as MaterialButton
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) as MaterialButton

            viewThemeUtils.material.colorMaterialButtonPrimaryTonal(positiveButton!!)
            viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(negativeButton)
        }
    }

    override fun onResume() {
        super.onResume()
        bindButton()
        keyboardUtils.showKeyboardForEditText(requireDialog().window, binding.userInput)
    }

    @Suppress("EmptyFunctionBlock")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mParentFolder = arguments?.getParcelableArgument(ARG_PARENT_FOLDER, OCFile::class.java)

        val inflater = requireActivity().layoutInflater
        binding = EditBoxDialogBinding.inflate(inflater, null, false)
        val view: View = binding.root

        binding.userInput.setText(R.string.empty)
        viewThemeUtils.material.colorTextInputLayout(binding.userInputContainer)

        val parentFolder = requireArguments().getParcelableArgument(ARG_PARENT_FOLDER, OCFile::class.java)

        val folderContent = fileDataStorageManager.getFolderContent(parentFolder, false)
        val fileNames: MutableSet<String> = Sets.newHashSetWithExpectedSize(folderContent.size)
        for (file in folderContent) {
            fileNames.add(file.fileName)
        }

        binding.userInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                checkFileNameAfterEachType(fileNames)
            }
        })

        val builder = buildMaterialAlertDialog(view)
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(binding.userInputContainer.context, builder)
        return builder.create()
    }

    private fun checkFileNameAfterEachType(fileNames: MutableSet<String>) {
        val newFileName = binding.userInput.text?.toString()?.trim() ?: ""
        val errorMessageId: Int? = FileNameValidator.isValid(newFileName)?.messageId

        val error = when {
            newFileName.isEmpty() -> null
            newFileName[0] == '.' -> R.string.hidden_file_name_warning
            errorMessageId != null -> errorMessageId
            fileNames.contains(newFileName) -> R.string.file_already_exists
            else -> null
        }

        if (error != null) {
            binding.userInputContainer.error = getString(error)
            positiveButton?.isEnabled = false
            if (positiveButton == null) {
                bindButton()
            }
        } else {
            binding.userInputContainer.error = null
            binding.userInputContainer.isErrorEnabled = false
            positiveButton?.isEnabled = true
        }
    }

    private fun buildMaterialAlertDialog(view: View): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(requireActivity())
            .setView(view)
            .setPositiveButton(R.string.folder_confirm_create, this)
            .setNegativeButton(R.string.common_cancel, this)
            .setTitle(R.string.uploader_info_dirname)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            val newFolderName = (getDialog()!!.findViewById<View>(R.id.user_input) as TextView)
                .text.toString().trim { it <= ' ' }

            val errorMessageId: Int? = FileNameValidator.isValid(newFolderName)?.messageId

            if (errorMessageId != null) {
                DisplayUtils.showSnackMessage(requireActivity(), errorMessageId)
                return
            }

            val path = mParentFolder?.decryptedRemotePath + newFolderName + OCFile.PATH_SEPARATOR
            if (requireActivity() is ComponentsGetter) {
                (requireActivity() as ComponentsGetter).fileOperationsHelper.createFolder(path)
            }
        }
    }

    companion object {
        private const val ARG_PARENT_FOLDER = "PARENT_FOLDER"
        const val CREATE_FOLDER_FRAGMENT = "CREATE_FOLDER_FRAGMENT"

        /**
         * Public factory method to create new CreateFolderDialogFragment instances.
         *
         * @param parentFolder Folder to create
         * @return Dialog ready to show.
         */
        @JvmStatic
        fun newInstance(parentFolder: OCFile?): CreateFolderDialogFragment {
            val bundle = Bundle().apply {
                putParcelable(ARG_PARENT_FOLDER, parentFolder)
            }

            return CreateFolderDialogFragment().apply {
                arguments = bundle
            }
        }
    }
}
