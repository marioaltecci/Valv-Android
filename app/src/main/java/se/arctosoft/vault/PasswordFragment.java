package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

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
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import se.arctosoft.vault.data.DirHash;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.utils.ViewAnimations;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class PasswordFragment extends Fragment {
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    private PasswordViewModel passwordViewModel;
    private SavedStateHandle savedStateHandle;
    private FragmentPasswordBinding binding;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private Settings settings;
    
    private boolean isCreateMode = false;
    private Uri targetDirectoryUri; // EN: Selected directory URI / RU: URI выбранной директории

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        settings = Settings.getInstance(requireContext());

        // EN: Safely get arguments / RU: Безопасно получаем аргументы
        Bundle args = getArguments();
        if (args != null) {
            isCreateMode = args.getBoolean("is_create_mode", false);
            String uriString = args.getString("file_uri");
            if (uriString != null) targetDirectoryUri = Uri.parse(uriString);
        }

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        // --- UI SETUP ---
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

        binding.btnUnlock.setEnabled(false);
        binding.btnUnlock.setText(isCreateMode ? "Create Vault" : "Unlock");

        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int length = (s != null) ? s.length() : 0;
                binding.btnUnlock.setEnabled(length > 0 && length <= 60);
                if (length > 60) {
                    binding.textField.setError("Max 60 characters");
                    ViewAnimations.shakeView(binding.textField);
                } else {
                    binding.textField.setError(null);
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
            toggleLoading(true, false);
            char[] temp = new char[length];
            binding.eTPassword.getText().getChars(0, length, temp, 0);
            performUnlock(temp, false);
        });

        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));

        // --- BIOMETRICS ---
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (!isCreateMode && settings.isBiometricsEnabled() && biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            setupBiometrics();
        } else {
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    private void performUnlock(char[] password, boolean isBiometric) {
        new Thread(() -> {
            try {
                DirHash dirHash = settings.getDirHashForKey(password);
                
                if (!isCreateMode && dirHash == null) {
                    showError("Vault not found or wrong password");
                    return;
                }

                if (isCreateMode) {
                    byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                    dirHash = Encryption.getDirHash(salt, password);
                    
                    if (dirHash != null) {
                        // EN: Register hash and prepare singleton / RU: Регистрируем хеш и готовим синглтон
                        settings.createDirHashEntry(salt, dirHash.hash());
                        Password.getInstance().setDirHash(dirHash);
                        
                        if (targetDirectoryUri != null) {
                            // EN: Link folder to app logic / RU: Привязываем папку к логике приложения
                            settings.addGalleryDirectory(targetDirectoryUri, true, null);
                        }
                    } else {
                        throw new Exception("Hash failed");
                    }
                }

                final DirHash finalDirHash = dirHash;
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        binding.eTPassword.clearFocus();
                        passwordViewModel.setPassword(password);
                        passwordViewModel.setDirHash(finalDirHash);
                        
                        binding.eTPassword.setText(null);
                        MainActivity.GLIDE_KEY = System.currentTimeMillis();
                        savedStateHandle.set(LOGIN_SUCCESSFUL, true);
                        
                        // EN: Delay navigation slightly for UI smoothness / RU: Небольшая задержка перехода для плавности
                        binding.getRoot().postDelayed(() -> {
                            if (isAdded()) {
                                if (isCreateMode) {
                                    // EN: Navigate forward if creating new vault / RU: Переходим вперед, если создаем новое
                                    Navigation.findNavController(requireView()).navigate(R.id.action_password_to_main);
                                } else {
                                    // EN: Go back if unlocking existing / RU: Возвращаемся назад, если открываем старое
                                    NavHostFragment.findNavController(this).popBackStack();
                                }
                            }
                        }, 50);
                    });
                }
            } catch (Exception e) {
                showError("Error: " + e.getMessage());
            }
        }).start();
    }

    private void showError(String message) {
        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                toggleLoading(false, false);
                Toaster.getInstance(requireActivity()).showShort(message);
            });
        }
    }

    private void setupBiometrics() {
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                requireActivity().runOnUiThread(() -> {
                    toggleLoading(true, true);
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (cryptoObject != null) {
                        try {
                            byte[] decrypted = cryptoObject.getCipher().doFinal(settings.getBiometricsData());
                            char[] chars = Encryption.toChars(decrypted);
                            performUnlock(chars, true);
                        } catch (Exception e) {
                            toggleLoading(false, true);
                            Toaster.getInstance(requireActivity()).showShort("Biometric error");
                        }
                    }
                });
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                requireActivity().runOnUiThread(() -> toggleLoading(false, true));
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
    }

    private void toggleLoading(boolean isLoading, boolean isBiometric) {
        if (isLoading) {
            CircularProgressDrawable progress = new CircularProgressDrawable(requireContext());
            progress.setStyle(CircularProgressDrawable.DEFAULT);
            int color = isBiometric ? ContextCompat.getColor(requireContext(), R.color.primary_color) : 0xFFFFFFFF;
            progress.setColorSchemeColors(color);
            progress.setStrokeWidth(8f);
            progress.setCenterRadius(20f);
            progress.start();

            binding.eTPassword.setEnabled(false);
            binding.btnBack.setEnabled(false);
            if (isBiometric) {
                binding.biometrics.setIcon(progress);
                binding.btnUnlock.setEnabled(false);
            } else {
                binding.btnUnlock.setIcon(progress);
                binding.biometrics.setEnabled(false);
            }
        } else {
            binding.biometrics.setIconResource(R.drawable.rounded_fingerprint_24);
            binding.btnUnlock.setIcon(null);
            binding.btnUnlock.setEnabled(binding.eTPassword.length() > 0);
            binding.biometrics.setEnabled(true);
            binding.eTPassword.setEnabled(true);
            binding.btnBack.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
            }
