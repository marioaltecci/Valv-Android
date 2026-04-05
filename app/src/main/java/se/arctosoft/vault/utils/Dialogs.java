/*
 * Valv-Android
 * Copyright (c) 2023 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.util.LinkedList;
import java.util.List;

import se.arctosoft.vault.BuildConfig;
import se.arctosoft.vault.R;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.databinding.DialogEditNoteBinding;
import se.arctosoft.vault.databinding.DialogImportTextBinding;
import se.arctosoft.vault.databinding.DialogSetIterationCountBinding;
import se.arctosoft.vault.interfaces.IOnEdited;

public class Dialogs {
    private static final String TAG = "Dialogs";

    // EN: Interface for password result callback / RU: Интерфейс для колбэка результата пароля
    public interface IOnPasswordEntered {
        void onResult(String password);
    }

    public interface IOnDirectorySelected {
        void onDirectorySelected(@NonNull DocumentFile directory, boolean deleteOriginal);
        void onOtherDirectory();
    }

    public interface IOnPositionSelected {
        void onSelected(int pos);
    }

    public interface IOnEditedIncludedFolders {
        void onRemoved(@NonNull List<Uri> selectedToRemove);
    }

    public static void showCopyMoveChooseDestinationDialog(FragmentActivity context, Settings settings, int fileCount, IOnDirectorySelected onDirectorySelected) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = FileStuff.getFilenameWithPathFromUri(directories.get(i));
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_import_to_title))
                .setItems(names, (dialog, which) -> {
                    Uri uri = directories.get(which);
                    DocumentFile directory = DocumentFile.fromTreeUri(context, uri);
                    if (directory == null || !directory.isDirectory() || !directory.exists()) {
                        settings.removeGalleryDirectory(uri);
                        Toaster.getInstance(context).showLong(context.getString(R.string.directory_does_not_exist));
                        showCopyMoveChooseDestinationDialog(context, settings, fileCount, onDirectorySelected);
                    } else {
                        onDirectorySelected.onDirectorySelected(directory, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.dialog_import_to_button_neutral, (dialog, which) -> onDirectorySelected.onOtherDirectory())
                .show();
    }

    public static void showTextDialog(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showAboutDialog(Context context) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_about_title))
                .setMessage(context.getString(R.string.dialog_about_message, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(context.getString(R.string.licenses), (dialogInterface, i) -> {
                    new LibsBuilder()
                            .withActivityTitle(context.getString(R.string.licenses))
                            .start(context);
                })
                .show();
    }

    public static void showConfirmationDialog(Context context, String title, String message, DialogInterface.OnClickListener onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, onConfirm)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showEditNoteDialog(FragmentActivity context, @Nullable String editTextBody, IOnEdited onEdited) {
        DialogEditNoteBinding binding = DialogEditNoteBinding.inflate(context.getLayoutInflater(), null, false);
        if (editTextBody != null) {
            binding.text.setText(editTextBody);
        }

        new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.gallery_note_save, (dialog, which) -> onEdited.onEdited(binding.text.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.gallery_note_delete, (dialog, which) -> onEdited.onEdited(null))
                .show();
    }

    public static void showImportTextDialog(FragmentActivity context, @Nullable String editTextBody, boolean isEdit, IOnEdited onEdited) {
        DialogImportTextBinding binding = DialogImportTextBinding.inflate(context.getLayoutInflater(), null, false);
        if (editTextBody != null) {
            binding.text.setText(editTextBody);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.gallery_import_text_title))
                .setView(binding.getRoot())
                .setPositiveButton(isEdit ? R.string.gallery_import_text_overwrite : R.string.gallery_import_text_import, (dialog, which) -> onEdited.onEdited(binding.text.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showSetIterationCountDialog(FragmentActivity context, @Nullable String editTextBody, IOnEdited onEdited) {
        DialogSetIterationCountBinding binding = DialogSetIterationCountBinding.inflate(context.getLayoutInflater(), null, false);
        if (editTextBody != null) {
            binding.text.setText(editTextBody);
        }
        binding.text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int ic = Integer.parseInt(s.toString());
                    if (ic > 500000) {
                        binding.text.setText(String.valueOf(500000));
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.settings_iteration_count_title))
                .setView(binding.getRoot())
                .setPositiveButton(R.string.save, (dialog, which) -> onEdited.onEdited(binding.text.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showEditIncludedFolders(Context context, @NonNull Settings settings, @NonNull IOnEditedIncludedFolders onEditedIncludedFolders) {
        List<Uri> directories = settings.getGalleryDirectoriesAsUri(false);
        String[] names = new String[directories.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = FileStuff.getFilenameWithPathFromUri(directories.get(i));
        }
        List<Uri> selectedToRemove = new LinkedList<>();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.dialog_edit_included_title))
                .setMultiChoiceItems(names, null, (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedToRemove.add(directories.get(which));
                    } else {
                        selectedToRemove.remove(directories.get(which));
                    }
                })
                .setPositiveButton(context.getString(R.string.remove), (dialog, which) -> onEditedIncludedFolders.onRemoved(selectedToRemove))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // EN: Show password dialog for a specific file/folder / RU: Показать диалог ввода пароля для файла или папки
    public static void showPasswordDialog(Context context, GalleryFile file, IOnPasswordEntered callback) {
        final EditText editText = new EditText(context);
        // EN: Set input to password style / RU: Установка стиля ввода "пароль"
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        new MaterialAlertDialogBuilder(context)
                .setTitle(file.getName())
                // EN: Using the R.string.password resource we just added / RU: Используем ресурс R.string.password
                .setMessage(R.string.password)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (callback != null) {
                        callback.onResult(editText.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
                                          }
