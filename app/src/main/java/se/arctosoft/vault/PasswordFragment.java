package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

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
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.utils.ViewAnimations;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

// EN: Updated fragment implementing "Folder Selection -> Password" logic
// RU: Обновленный фрагмент, реализующий логику "Выбор папки -> Пароль"
public class PasswordFragment extends Fragment {
    private static final String TAG = "PasswordFragment";
    private static final int PICK_FOLDER_REQUEST = 1001;
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    private PasswordViewModel passwordViewModel;
    private SavedStateHandle savedStateHandle;
    private FragmentPasswordBinding binding;
    private Uri selectedFolderUri;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // --- EDGE TO EDGE SETUP ---
        Window window = requireActivity().getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        Settings settings = Settings.getInstance(requireContext());

        // --- UI ANIMATIONS & INITIAL STATE ---
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        
        // EN: Reset views for the new flow / RU: Сбрасываем вьюхи для нового флоу
        binding.actionsContainer.setVisibility(View.VISIBLE);
        binding.passwordContainer.setVisibility(View.GONE);

        // --- FOLDER SELECTION (THE HORSE) ---
        binding.btnOpenVault.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, PICK_FOLDER_REQUEST);
        });

        binding.btnCreateVault.setOnClickListener(v -> {
            // EN: Handle creation logic here / RU: Логика создания нового хранилища
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, PICK_FOLDER_REQUEST);
        });

        // --- PASSWORD INPUT LOGIC (THE CART) ---
        binding.btnUnlock.setEnabled(false);
        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                int length = (s != null) ? s.length() : 0;
                binding.btnUnlock.setEnabled(length > 0 && length <= 60);
            }
        });

        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnUnlock.isEnabled()) binding.btnUnlock.performClick();
                return true;
            }
            return false;
        });

        binding.btnUnlock.setOnClickListener(v -> unlockVault());

        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, "First, select the folder where your encrypted files are located. Then enter your password to unlock."));

        // --- BIOMETRICS SETUP ---
        setupBiometrics(settings);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedFolderUri = data.getData();
            if (selectedFolderUri != null) {
                // EN: Show password input after folder is picked / RU: Показываем ввод пароля после выбора папки
                binding.actionsContainer.setVisibility(View.GONE);
                binding.passwordContainer.setVisibility(View.VISIBLE);
                binding.eTPassword.requestFocus();
            }
        }
    }

    private void unlockVault() {
        int length = binding.eTPassword.length();
        if (length == 0 || length > 60) return;

        binding.btnUnlock.setEnabled(false);
        binding.eTPassword.setEnabled(false);
        // binding.loading.setVisibility(View.VISIBLE); // EN: If you added loading to XML / RU: Если добавил лоадинг в XML

        char[] password = new char[length];
        binding.eTPassword.getText().getChars(0, length, password, 0);

        new Thread(() -> {
            try {
                // EN: Pass URI and password to the encryption engine / RU: Передаем URI и пароль в движок шифрования
                boolean success = passwordViewModel.initializeVault(requireContext(), selectedFolderUri, password);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (success) {
                            binding.eTPassword.clearFocus();
                            binding.eTPassword.setText(null);
                            MainActivity.GLIDE_KEY = System.currentTimeMillis();
                            savedStateHandle.set(LOGIN_SUCCESSFUL, true);
                            
                            // EN: Navigate to Gallery / RU: Переход в галерею
                            NavHostFragment.findNavController(this).popBackStack();
                        } else {
                            handleAuthError("Invalid password or folder");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Login failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> handleAuthError("Authentication error"));
                }
            }
        }).start();
    }

    private void handleAuthError(String error) {
        binding.btnUnlock.setEnabled(true);
        binding.eTPassword.setEnabled(true);
        // binding.loading.setVisibility(View.GONE);
        Toaster.getInstance(requireActivity()).showShort(error);
        ViewAnimations.shakeView(binding.textField);
    }

    private void setupBiometrics(Settings settings) {
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (settings.isBiometricsEnabled() && biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            Executor executor = ContextCompat.getMainExecutor(requireContext());
            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (cryptoObject != null) {
                        try {
                            byte[] decrypted = cryptoObject.getCipher().doFinal(settings.getBiometricsData());
                            char[] chars = Encryption.toChars(decrypted);
                            binding.eTPassword.setText(chars, 0, chars.length);
                            binding.btnUnlock.performClick();
                        } catch (Exception e) {
                            Toaster.getInstance(requireActivity()).showShort("Biometric decryption error");
                        }
                    }
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric Unlock")
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build();

            binding.biometrics.setOnClickListener(v -> {
                try {
                    Cipher cipher = Encryption.getBiometricCipher();
                    SecretKey secretKey = Encryption.getOrGenerateBiometricSecretKey();
                    byte[] iv = settings.getBiometricsIv();
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
                } catch (Exception e) {
                    Toaster.getInstance(requireContext()).showShort("Biometrics unavailable");
                }
            });
        } else {
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
