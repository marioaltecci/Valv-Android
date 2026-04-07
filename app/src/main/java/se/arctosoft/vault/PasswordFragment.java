package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import se.arctosoft.vault.data.DirHash;
import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.utils.ViewAnimations;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class PasswordFragment extends Fragment {
    private static final String TAG = "PasswordFragment";
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    private PasswordViewModel passwordViewModel;
    private SavedStateHandle savedStateHandle;
    private FragmentPasswordBinding binding;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    // Для списка последних хранилищ
    private RecyclerView rvRecent;
    private LinearLayout recentContainer;
    private RecentVaultsAdapter recentAdapter;
    private String selectedVaultPath = null;
    private boolean isCreatingNew = false;

    // ActivityResultLauncher для выбора папки
    private ActivityResultLauncher<Intent> createVaultLauncher;
    private ActivityResultLauncher<Intent> openVaultLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        Settings settings = Settings.getInstance(requireContext());

        // Регистрация лаунчеров для выбора папок
        registerLaunchers();

        // --- UI ANIMATIONS ---
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        binding.logoContainer.setOnClickListener(v -> ViewAnimations.shakeView(binding.ivLogo));

        // --- НОВЫЙ UI (One UI стиль) ---
        rvRecent = binding.rvRecent;
        recentContainer = binding.recentContainer;
        com.google.android.material.button.MaterialButton btnCreateNew = binding.btnCreateNew;
        com.google.android.material.button.MaterialButton btnOpenFolder = binding.btnOpenFolder;

        // Загружаем список последних хранилищ
        List<String> recentVaults = settings.getRecentVaults();
        if (recentVaults != null && !recentVaults.isEmpty()) {
            recentContainer.setVisibility(View.VISIBLE);
            rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
            recentAdapter = new RecentVaultsAdapter(recentVaults, path -> {
                selectedVaultPath = path;
                isCreatingNew = false;
                binding.eTPassword.requestFocus();
                Toaster.getInstance(requireContext()).showShort("Введите пароль для: " + path);
            });
            rvRecent.setAdapter(recentAdapter);
        } else {
            recentContainer.setVisibility(View.GONE);
        }

        // Кнопка "Создать новое хранилище"
        btnCreateNew.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            createVaultLauncher.launch(intent);
        });

        // Кнопка "Открыть папку"
        btnOpenFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            openVaultLauncher.launch(intent);
        });

        // --- PASSWORD INPUT LOGIC ---
        binding.btnUnlock.setEnabled(false);
        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                int length = (s != null) ? s.length() : 0;
                if (length > 60) {
                    binding.textField.setError("Max 60 characters");
                    binding.btnUnlock.setEnabled(false);
                } else {
                    binding.textField.setError(null);
                    binding.btnUnlock.setEnabled(length > 0);
                }
            }
        });

        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnUnlock.isEnabled()) binding.btnUnlock.performClick();
                return true;
            }
            return false;
        });

        binding.btnUnlock.setOnClickListener(v -> {
            int length = binding.eTPassword.length();
            if (length == 0 || length > 60) return;

            binding.btnUnlock.setEnabled(false);
            binding.eTPassword.setEnabled(false);
            binding.biometrics.setEnabled(false);
            binding.loading.setVisibility(View.VISIBLE);

            char[] temp = new char[length];
            binding.eTPassword.getText().getChars(0, length, temp, 0);
            passwordViewModel.setPassword(temp);

            new Thread(() -> {
                try {
                    DirHash dirHash = settings.getDirHashForKey(temp);
                    
                    // Если режим открытия существующего хранилища и хэша нет - ошибка
                    if (selectedVaultPath != null && !isCreatingNew && dirHash == null) {
                        throw new Exception("No vault found at this path");
                    }
                    
                    if (dirHash == null) {
                        byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                        dirHash = Encryption.getDirHash(salt, temp);
                        if (dirHash != null) {
                            settings.createDirHashEntry(salt, dirHash.hash());
                            // Сохраняем путь в список последних
                            if (selectedVaultPath != null) {
                                settings.addRecentVault(selectedVaultPath);
                            }
                        } else {
                            throw new Exception("Hash error");
                        }
                    } else {
                        // Успешный вход - сохраняем путь в список последних
                        if (selectedVaultPath != null) {
                            settings.addRecentVault(selectedVaultPath);
                        }
                    }

                    DirHash finalDirHash = dirHash;
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            binding.eTPassword.clearFocus();
                            
                            passwordViewModel.setDirHash(finalDirHash);
                            binding.eTPassword.setText(null);
                            MainActivity.GLIDE_KEY = System.currentTimeMillis();
                            savedStateHandle.set(LOGIN_SUCCESSFUL, true);
                            
                            binding.getRoot().postDelayed(() -> {
                                if (isAdded()) {
                                    NavHostFragment.findNavController(this).popBackStack();
                                }
                            }, 50);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Login failed", e);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            binding.btnUnlock.setEnabled(true);
                            binding.eTPassword.setEnabled(true);
                            binding.biometrics.setEnabled(true);
                            binding.loading.setVisibility(View.GONE);
                            Toaster.getInstance(requireActivity()).showShort(e.getMessage() != null ? e.getMessage() : "Authentication error");
                        });
                    }
                }
            }).start();
        });

        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));

        // --- BIOMETRICS SETUP ---
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
                    .setTitle(getString(R.string.biometrics_unlock_title))
                    .setNegativeButtonText(getString(R.string.cancel))
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
            
            binding.biometrics.post(() -> binding.biometrics.performClick());
        } else {
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    private void registerLaunchers() {
        createVaultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                Uri treeUri = result.getData().getData();
                String path = getPathFromUri(treeUri);
                if (path != null) {
                    isCreatingNew = true;
                    selectedVaultPath = path;
                    binding.eTPassword.requestFocus();
                    Toaster.getInstance(requireContext()).showShort("Создание: придумайте пароль");
                }
            }
        });

        openVaultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                Uri treeUri = result.getData().getData();
                String path = getPathFromUri(treeUri);
                if (path != null) {
                    isCreatingNew = false;
                    selectedVaultPath = path;
                    binding.eTPassword.requestFocus();
                    Toaster.getInstance(requireContext()).showShort("Введите пароль для: " + path);
                }
            }
        });
    }

    private String getPathFromUri(Uri uri) {
        // Простой вариант - возвращаем строковое представление URI
        // В реальном приложении нужно получить реальный путь через ContentResolver
        if (uri != null) {
            return uri.toString();
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
