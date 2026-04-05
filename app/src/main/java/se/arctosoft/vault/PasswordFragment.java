package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
    private Settings settings;

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

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        // --- UI ANIMATIONS ---
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        binding.logoContainer.setOnClickListener(v -> ViewAnimations.shakeView(binding.ivLogo));

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
                    ViewAnimations.shakeView(binding.textField);
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

        // --- UNLOCK BUTTON ACTION ---
        // --- ДЕЙСТВИЕ КНОПКИ РАЗБЛОКИРОВКИ ---
        binding.btnUnlock.setOnClickListener(v -> {
            int length = binding.eTPassword.length();
            if (length == 0 || length > 60) return;

            toggleLoading(true, false); // EN: Loading on button / RU: Загрузка на кнопке

            char[] temp = new char[length];
            binding.eTPassword.getText().getChars(0, length, temp, 0);
            
            // EN: Start validation process / RU: Запуск процесса проверки
            performUnlock(temp, false);
        });

        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));

        // --- BIOMETRICS SETUP ---
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (settings.isBiometricsEnabled() && biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            setupBiometrics();
        } else {
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    private void setupBiometrics() {
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                requireActivity().runOnUiThread(() -> {
                    // EN: Loading on biometric icon only / RU: Загрузка только на иконке отпечатка
                    toggleLoading(true, true);
                    
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (cryptoObject != null) {
                        try {
                            byte[] decrypted = cryptoObject.getCipher().doFinal(settings.getBiometricsData());
                            char[] chars = Encryption.toChars(decrypted);
                            
                            // EN: Direct unlock without setting text or clicking button
                            // RU: Прямая разблокировка без вставки текста и клика по кнопке
                            performUnlock(chars, true);
                        } catch (Exception e) {
                            toggleLoading(false, true);
                            Toaster.getInstance(requireActivity()).showShort("Biometric decryption error");
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

    /**
     * EN: Common logic for both password and biometric unlock
     * RU: Общая логика для разблокировки паролем и биометрией
     */
    private void performUnlock(char[] password, boolean isBiometric) {
        passwordViewModel.setPassword(password);

        new Thread(() -> {
            try {
                DirHash dirHash = settings.getDirHashForKey(password);
                if (dirHash == null) {
                    byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                    dirHash = Encryption.getDirHash(salt, password);
                    if (dirHash != null) {
                        settings.createDirHashEntry(salt, dirHash.hash());
                    } else {
                        throw new Exception("Hash error");
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
                Log.e(TAG, "Unlock failed", e);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        toggleLoading(false, isBiometric);
                        Toaster.getInstance(requireActivity()).showShort("Authentication error");
                    });
                }
            }
        }).start();
    }

    /**
     * EN: Switches visual loading state between buttons
     * RU: Переключает визуальное состояние загрузки для кнопок
     */
    private void toggleLoading(boolean isLoading, boolean isBiometric) {
        if (isLoading) {
            CircularProgressDrawable progress = new CircularProgressDrawable(requireContext());
            progress.setStyle(CircularProgressDrawable.DEFAULT);
            progress.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.primary_color));
            progress.setStrokeWidth(5f);
            progress.setCenterRadius(20f);
            progress.start();

            binding.eTPassword.setEnabled(false);
            if (isBiometric) {
                binding.biometrics.setIcon(progress);
                binding.btnUnlock.setEnabled(false);
            } else {
                binding.btnUnlock.setIcon(progress);
                binding.biometrics.setEnabled(false);
            }
        } else {
            // EN: Restore original icons and states
            // RU: Возвращаем иконки и состояние
            binding.biometrics.setIconResource(R.drawable.rounded_fingerprint_24);
            binding.btnUnlock.setIcon(null);
            binding.btnUnlock.setEnabled(binding.eTPassword.length() > 0);
            binding.biometrics.setEnabled(true);
            binding.eTPassword.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
                            }
