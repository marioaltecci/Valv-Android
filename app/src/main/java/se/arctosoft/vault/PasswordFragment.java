package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;  // ✅ ДОБАВЛЕН ИМПОРТ

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import se.arctosoft.vault.data.DirHash;
import se.arctosoft.vault.databinding.FragmentPasswordBinding;
import se.arctosoft.vault.encryption.Encryption;
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

    // EN: Custom counter TextView for password length display | RU: Свой счётчик для отображения длины пароля
    private TextView customCharCounter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (binding == null) return;
        
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);

        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        Settings settings = Settings.getInstance(requireContext());

        // --- UI ANIMATIONS ---
        // EN: Setup elastic animation for logo container | RU: Настройка упругой анимации для контейнера логотипа
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        binding.logoContainer.setOnClickListener(v -> ViewAnimations.shakeView(binding.ivLogo));

        // --- INITIAL UI STATE ---
        // EN: Button is hidden until user types | RU: Кнопка скрыта, пока пользователь не начнёт ввод
        binding.btnUnlock.setVisibility(View.GONE);
        binding.btnUnlock.setEnabled(false);

        // --- CUSTOM CHARACTER COUNTER SETUP ---
        // EN: Initialize custom counter from layout | RU: Инициализация пользовательского счётчика из разметки
        customCharCounter = view.findViewById(R.id.tvCharCounter);
        if (customCharCounter != null) {
            customCharCounter.setText("0/60");
        }

        // --- PASSWORD INPUT LOGIC WITH COUNTER ---
        // EN: Password input logic with custom character counter | RU: Логика ввода пароля с пользовательским счётчиком
        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override 
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override 
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                int length = (s != null) ? s.length() : 0;
                
                // --- EN: Update custom counter display | RU: Обновление отображения пользовательского счётчика ---
                if (customCharCounter != null) {
                    customCharCounter.setText(length + "/60");
                    
                    // EN: Change color to red if limit exceeded (max 60 chars) | RU: Меняем цвет на красный при превышении лимита
                    if (length > 60) {
                        customCharCounter.setTextColor(Color.RED);
                    } else {
                        // EN: Set to secondary text color (gray) | RU: Устанавливаем вторичный цвет текста (серый)
                        customCharCounter.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.secondary_text_dark));
                    }
                }
                
                // EN: Toggle unlock button visibility | RU: Переключение видимости кнопки разблокировки
                if (length > 0 && length <= 60) {
                    if (binding.btnUnlock.getVisibility() != View.VISIBLE) {
                        binding.btnUnlock.setAlpha(0f);
                        binding.btnUnlock.setVisibility(View.VISIBLE);
                        binding.btnUnlock.animate().alpha(1f).setDuration(200).start();
                    }
                    binding.btnUnlock.setEnabled(true);
                } else {
                    if (length == 0) {
                        binding.btnUnlock.setVisibility(View.GONE);
                    }
                    binding.btnUnlock.setEnabled(false);
                }

                // EN: Show error if max length exceeded | RU: Показываем ошибку при превышении максимальной длины
                if (length > 60) {
                    binding.textField.setError("Max 60 characters");
                    ViewAnimations.shakeView(binding.textField);
                } else {
                    binding.textField.setError(null);
                }
            }
        });

        // EN: Handle keyboard action (GO/DONE) | RU: Обработка действия клавиатуры (GO/ГОТОВО)
        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnUnlock.isEnabled()) binding.btnUnlock.performClick();
                return true;
            }
            return false;
        });

        // EN: Auto-focus handling | RU: Обработка автоматического фокуса
        binding.eTPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && binding.eTPassword.length() > 0 && binding.eTPassword.length() <= 60) {
                if (binding.btnUnlock.getVisibility() != View.VISIBLE) {
                    binding.btnUnlock.setVisibility(View.VISIBLE);
                }
            }
        });

        // --- UNLOCK BUTTON LOGIC ---
        // EN: Unlock button click handler | RU: Обработчик нажатия кнопки разблокировки
        binding.btnUnlock.setOnClickListener(v -> {
            int length = binding.eTPassword.length();
            if (length == 0 || length > 60) return;

            // EN: Disable UI during authentication | RU: Блокируем интерфейс на время аутентификации
            binding.btnUnlock.setEnabled(false);
            binding.eTPassword.setEnabled(false);
            binding.biometrics.setEnabled(false);
            binding.loading.setVisibility(View.VISIBLE);

            // EN: Get password characters | RU: Получаем символы пароля
            char[] temp = new char[length];
            binding.eTPassword.getText().getChars(0, length, temp, 0);
            passwordViewModel.setPassword(temp);

            // EN: Background thread for encryption check | RU: Фоновый поток для проверки шифрования
            new Thread(() -> {
                try {
                    DirHash dirHash = settings.getDirHashForKey(temp);
                    if (dirHash == null) {
                        byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                        dirHash = Encryption.getDirHash(salt, temp);
                        if (dirHash != null) {
                            settings.createDirHashEntry(salt, dirHash.hash());
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
                            // EN: Re-enable UI on failure | RU: Разблокируем интерфейс при ошибке
                            binding.btnUnlock.setEnabled(true);
                            binding.eTPassword.setEnabled(true);
                            binding.biometrics.setEnabled(true);
                            binding.loading.setVisibility(View.GONE);
                            Toaster.getInstance(requireActivity()).showShort("Authentication error");
                        });
                    }
                }
            }).start();
        });

        // --- DYNAMIC HELP BUTTON COLOR LOGIC ---
        // EN: Help button with color change effect | RU: Кнопка помощи с эффектом смены цвета
        binding.btnHelp.setOnClickListener(v -> {
            // EN: Save the original icon tint (usually gray) | RU: Сохраняем оригинальный цвет иконки (обычно серый)
            ColorStateList originalTint = binding.btnHelp.getIconTint();
            
            // EN: Change icon color to Yellow (Hex: #FFC107 - Material Amber) | RU: Меняем цвет иконки на жёлтый
            binding.btnHelp.setIconTint(ColorStateList.valueOf(Color.parseColor("#FFC107")));

            // EN: Create and show dialog directly to track dismissal | RU: Создаём и показываем диалог, чтобы отследить его закрытие
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.launcher_help_message))
                    .setPositiveButton(android.R.string.ok, null)
                    .setOnDismissListener(dialog -> {
                        // EN: Restore original color when dialog closes | RU: Возвращаем исходный цвет при закрытии диалога
                        binding.btnHelp.setIconTint(originalTint);
                    })
                    .show();
        });

        // --- BIOMETRICS SETUP ---
        // EN: Biometric authentication setup | RU: Настройка биометрической аутентификации
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        if (settings.isBiometricsEnabled() && biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            Executor executor = ContextCompat.getMainExecutor(requireContext());
            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if (result.getCryptoObject() != null) {
                        try {
                            byte[] decrypted = result.getCryptoObject().getCipher().doFinal(settings.getBiometricsData());
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
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(settings.getBiometricsIv()));
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
                                                          }
