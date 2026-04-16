package se.arctosoft.vault;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.utils.ViewAnimations;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

// EN: Updated Fragment with "Folder First, Password Second" logic
// RU: Обновленный фрагмент с логикой "Сначала папка, потом пароль"
public class PasswordFragment extends Fragment {
    private static final String TAG = "PasswordFragment";
    private static final int PICK_FOLDER_REQUEST = 1001;

    private FragmentPasswordBinding binding;
    private PasswordViewModel passwordViewModel;
    private Uri selectedFolderUri;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // --- UI SETUP ---
        Window window = requireActivity().getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);

        // EN: Initial state: actions visible, password hidden
        // RU: Начальное состояние: действия видны, пароль скрыт
        binding.actionsContainer.setVisibility(View.VISIBLE);
        binding.passwordContainer.setVisibility(View.GONE);
        binding.btnUnlock.setEnabled(false);

        // --- BUTTON LOGIC ---

        binding.btnOpenVault.setOnClickListener(v -> {
            // EN: Open system folder picker / RU: Открываем системный выбор папки
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, PICK_FOLDER_REQUEST);
        });

        binding.btnCreateVault.setOnClickListener(v -> {
            // EN: Same picker, but we'll flag it as new vault creation
            // RU: Тот же выбор, но пометим как создание нового хранилища
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER_REQUEST);
        });

        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                binding.btnUnlock.setEnabled(s != null && s.length() > 0);
            }
        });

        binding.btnUnlock.setOnClickListener(v -> unlockVault());

        binding.btnHelp.setOnClickListener(v -> {
            // EN: Help info / RU: Инфо справка
            Toaster.getInstance(requireContext()).showShort("Select a folder to begin decryption.");
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedFolderUri = data.getData();
            if (selectedFolderUri != null) {
                // EN: Folder selected, now show password field
                // RU: Папка выбрана, теперь показываем поле пароля
                showPasswordInput();
            }
        }
    }

    private void showPasswordInput() {
        // EN: Transition animation / RU: Анимация перехода
        binding.actionsContainer.setVisibility(View.GONE);
        binding.passwordContainer.setVisibility(View.VISIBLE);
        binding.tvSubtitle.setText("Enter password for selected folder");
        binding.eTPassword.requestFocus();
    }

    private void unlockVault() {
        String passStr = binding.eTPassword.getText().toString();
        char[] password = passStr.toCharArray();

        binding.btnUnlock.setEnabled(false);
        // binding.loading.setVisibility(View.VISIBLE); // EN: If you have a progress bar / RU: Если есть прогресс-бар

        new Thread(() -> {
            try {
                // EN: Use ChaCha20 block to verify password against the folder
                // RU: Используем блок ChaCha20 для проверки пароля в этой папке
                // EN: We pass folder URI and password to ViewModel for processing
                // RU: Передаем URI папки и пароль в ViewModel для обработки
                boolean success = passwordViewModel.initializeWithFolder(requireContext(), selectedFolderUri, password);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            // EN: Go to gallery / RU: Переход в галерею
                            NavHostFragment.findNavController(this).navigate(R.id.action_password_to_gallery);
                        } else {
                            handleError("Invalid password or corrupted vault");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Decryption error", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> handleError("Access error"));
                }
            }
        }).start();
    }

    private void handleError(String message) {
        binding.btnUnlock.setEnabled(true);
        // binding.loading.setVisibility(View.GONE);
        Toaster.getInstance(requireContext()).showShort(message);
        ViewAnimations.shakeView(binding.textField);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
