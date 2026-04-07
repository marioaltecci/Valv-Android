package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

// Рус: Импорты для работы с биометрией (отпечаток/лицо)
// Eng: Imports for biometric authentication (fingerprint/face)
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

// Рус: Криптография - шифры, ключи, векторы инициализации
// Eng: Cryptography - ciphers, keys, initialization vectors
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

// Рус: Фрагмент экрана ввода пароля (разблокировка хранилища)
// Eng: Password entry screen fragment (vault unlock)
public class PasswordFragment extends Fragment {
    // Рус: Тег для логирования (отладка)
    // Eng: Logging tag (debugging)
    private static final String TAG = "PasswordFragment";
    // Рус: Ключ для сохранения состояния успешного входа (передача между фрагментами)
    // Eng: Key for saving successful login state (inter-fragment communication)
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    // Рус: ViewModel для хранения пароля и хэша директории
    // Eng: ViewModel for storing password and directory hash
    private PasswordViewModel passwordViewModel;
    // Рус: Хранилище сохранённого состояния (для передачи данных назад)
    // Eng: Saved state handle (for passing data back)
    private SavedStateHandle savedStateHandle;
    // Рус: Привязка к layout (View Binding)
    // Eng: View binding to layout
    private FragmentPasswordBinding binding;

    // Рус: Объект для биометрической аутентификации
    // Eng: Biometric authentication object
    private BiometricPrompt biometricPrompt;
    // Рус: Настройки/подсказки для биометрического диалога
    // Eng: Prompt configuration for biometric dialog
    private BiometricPrompt.PromptInfo promptInfo;

    // Рус: Создание View фрагмента (инфлейт layout)
    // Eng: Create fragment View (inflate layout)
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    // Рус: Настройка UI и логики после создания View
    // Eng: Setup UI and logic after View creation
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Рус: Получаем ViewModel, привязанную к активности (живёт дольше фрагмента)
        // Eng: Get ViewModel tied to activity (outlives fragment)
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);

        // Рус: Получаем SavedStateHandle из предыдущего стека навигации
        // Eng: Get SavedStateHandle from previous navigation back stack entry
        savedStateHandle = Navigation.findNavController(view)
                .getPreviousBackStackEntry()
                .getSavedStateHandle();
        // Рус: Устанавливаем флаг успешного входа в false (начальное состояние)
        // Eng: Set successful login flag to false (initial state)
        savedStateHandle.set(LOGIN_SUCCESSFUL, false);

        // Рус: Получаем синглтон настроек приложения
        // Eng: Get app settings singleton
        Settings settings = Settings.getInstance(requireContext());

        // --- UI ANIMATIONS (Анимации UI) ---
        // Рус: Настраиваем "эластичную" анимацию логотипа (нажатие/пружина)
        // Eng: Setup elastic logo animation (click/spring effect)
        ViewAnimations.setupElasticLogo(binding.logoContainer, binding.ivLogo);
        // Рус: При клике на контейнер логотипа - тряска иконки
        // Eng: On logo container click - shake the icon
        binding.logoContainer.setOnClickListener(v -> ViewAnimations.shakeView(binding.ivLogo));

        // --- PASSWORD INPUT LOGIC (Логика ввода пароля) ---
        // Рус: Изначально кнопка разблокировки выключена
        // Eng: Unlock button initially disabled
        binding.btnUnlock.setEnabled(false);
        // Рус: Слушатель изменений текста в поле пароля
        // Eng: Text change listener on password field
        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                // Рус: Проверка длины пароля (макс. 60 символов)
                // Eng: Check password length (max 60 chars)
                int length = (s != null) ? s.length() : 0;
                if (length > 60) {
                    // Рус: Показываем ошибку и трясем поле
                    // Eng: Show error and shake field
                    binding.textField.setError("Max 60 characters");
                    ViewAnimations.shakeView(binding.textField);
                    binding.btnUnlock.setEnabled(false);
                } else {
                    // Рус: Очищаем ошибку, включаем кнопку если есть хоть 1 символ
                    // Eng: Clear error, enable button if at least 1 char
                    binding.textField.setError(null);
                    binding.btnUnlock.setEnabled(length > 0);
                }
            }
        });

        // Рус: Обработка нажатия кнопки "Готово" на клавиатуре (IME action)
        // Eng: Handle "Done" button press on keyboard (IME action)
        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnUnlock.isEnabled()) binding.btnUnlock.performClick();
                return true;
            }
            return false;
        });

        // Рус: Обработчик нажатия кнопки "Разблокировать"
        // Eng: Unlock button click handler
        binding.btnUnlock.setOnClickListener(v -> {
            // Рус: Проверка длины (пусто или >60 - выход)
            // Eng: Check length (empty or >60 - exit)
            int length = binding.eTPassword.length();
            if (length == 0 || length > 60) return;

            // Рус: Отключаем UI на время проверки пароля
            // Eng: Disable UI during password verification
            binding.btnUnlock.setEnabled(false);
            binding.eTPassword.setEnabled(false);
            binding.biometrics.setEnabled(false);
            binding.loading.setVisibility(View.VISIBLE);

            // Рус: Копируем пароль из EditText в массив char[] (безопаснее строки)
            // Eng: Copy password from EditText to char[] array (safer than String)
            char[] temp = new char[length];
            binding.eTPassword.getText().getChars(0, length, temp, 0);
            passwordViewModel.setPassword(temp);

            // Рус: Запускаем проверку в отдельном потоке (не блокируем UI)
            // Eng: Run verification in background thread (don't block UI)
            new Thread(() -> {
                try {
                    // Рус: Пытаемся получить DirHash по паролю (хеш директории)
                    // Eng: Try to get DirHash from password (directory hash)
                    DirHash dirHash = settings.getDirHashForKey(temp);
                    if (dirHash == null) {
                        // Рус: Если хэша нет - создаём новый (первый вход/сброс)
                        // Eng: If no hash exists - create new one (first login/reset)
                        byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                        dirHash = Encryption.getDirHash(salt, temp);
                        if (dirHash != null) {
                            settings.createDirHashEntry(salt, dirHash.hash());
                        } else {
                            throw new Exception("Hash error");
                        }
                    }

                    // Рус: Сохраняем финальный DirHash для использования в лямбде
                    // Eng: Store final DirHash for lambda use
                    DirHash finalDirHash = dirHash;
                    // Рус: Проверяем, что фрагмент всё ещё прикреплён к активности
                    // Eng: Check that fragment is still attached to activity
                    if (isAdded()) {
                        // Рус: Переключаемся на UI-поток для обновления интерфейса
                        // Eng: Switch to UI thread for interface updates
                        requireActivity().runOnUiThread(() -> {
                            // --- PRE-EXIT STABILIZATION (Стабилизация перед выходом) ---
                            // 1. Clear focus to hide keyboard and stabilize layout
                            // 1. Убираем фокус, чтобы спрятать клаву и стабилизировать лайаут
                            binding.eTPassword.clearFocus();
                            
                            // Рус: Сохраняем DirHash в ViewModel, очищаем поле пароля
                            // Eng: Store DirHash in ViewModel, clear password field
                            passwordViewModel.setDirHash(finalDirHash);
                            binding.eTPassword.setText(null);
                            // Рус: Обновляем ключ Glide (чтобы перезагрузить изображения)
                            // Eng: Update Glide key (to reload images)
                            MainActivity.GLIDE_KEY = System.currentTimeMillis();
                            // Рус: Устанавливаем флаг успешного входа в true
                            // Eng: Set successful login flag to true
                            savedStateHandle.set(LOGIN_SUCCESSFUL, true);
                            
                            // 2. THE FIX: Tiny delay (50ms) to ensure background fragment is ready
                            // 2. ФИКС: Крошечная задержка (50мс), чтобы нижний фрагмент успел подготовиться
                            binding.getRoot().postDelayed(() -> {
                                if (isAdded()) {
                                    // Рус: Возвращаемся назад (закрываем фрагмент пароля)
                                    // Eng: Go back (close password fragment)
                                    NavHostFragment.findNavController(this).popBackStack();
                                }
                            }, 50);
                        });
                    }
                } catch (Exception e) {
                    // Рус: Ошибка аутентификации - логируем и восстанавливаем UI
                    // Eng: Authentication error - log and restore UI
                    Log.e(TAG, "Login failed", e);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
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

        // Рус: Кнопка "Помощь" - показываем диалог с текстом помощи
        // Eng: Help button - show dialog with help text
        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));

        // --- BIOMETRICS SETUP (Настройка биометрии) ---
        // Рус: Менеджер биометрии для проверки доступности
        // Eng: Biometric manager to check availability
        BiometricManager biometricManager = BiometricManager.from(requireContext());
        // Рус: Если биометрия включена в настройках И устройство поддерживает strong biometrics
        // Eng: If biometrics enabled in settings AND device supports strong biometrics
        if (settings.isBiometricsEnabled() && biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            // Рус: Executor для выполнения колбэков на главном потоке
            // Eng: Executor for callbacks on main thread
            Executor executor = ContextCompat.getMainExecutor(requireContext());
            // Рус: Создаём BiometricPrompt с колбэками
            // Eng: Create BiometricPrompt with callbacks
            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    // Рус: Получаем CryptoObject из результата аутентификации
                    // Eng: Get CryptoObject from authentication result
                    BiometricPrompt.CryptoObject cryptoObject = result.getCryptoObject();
                    if (cryptoObject != null) {
                        try {
                            // Рус: Расшифровываем сохранённые биометрические данные
                            // Eng: Decrypt stored biometric data
                            byte[] decrypted = cryptoObject.getCipher().doFinal(settings.getBiometricsData());
                            // Рус: Преобразуем байты в массив char и вставляем в поле пароля
                            // Eng: Convert bytes to char array and insert into password field
                            char[] chars = Encryption.toChars(decrypted);
                            binding.eTPassword.setText(chars, 0, chars.length);
                            // Рус: Автоматически нажимаем кнопку разблокировки
                            // Eng: Automatically click unlock button
                            binding.btnUnlock.performClick();
                        } catch (Exception e) {
                            Toaster.getInstance(requireActivity()).showShort("Biometric decryption error");
                        }
                    }
                }
            });

            // Рус: Настройки диалога биометрии (заголовок, кнопка отмены)
            // Eng: Biometric dialog configuration (title, cancel button)
            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.biometrics_unlock_title))
                    .setNegativeButtonText(getString(R.string.cancel))
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build();

            // Рус: Обработчик нажатия на кнопку биометрии
            // Eng: Biometric button click handler
            binding.biometrics.setOnClickListener(v -> {
                try {
                    // Рус: Получаем шифр для биометрии, ключ и вектор инициализации (IV)
                    // Eng: Get biometric cipher, secret key and initialization vector (IV)
                    Cipher cipher = Encryption.getBiometricCipher();
                    SecretKey secretKey = Encryption.getOrGenerateBiometricSecretKey();
                    byte[] iv = settings.getBiometricsIv();
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    // Рус: Запускаем биометрическую аутентификацию
                    // Eng: Start biometric authentication
                    biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
                } catch (Exception e) {
                    Toaster.getInstance(requireContext()).showShort("Biometrics unavailable");
                }
            });
            
            // Рус: Автоматически "нажимаем" кнопку биометрии после отрисовки (авто-запрос)
            // Eng: Automatically "click" biometric button after layout (auto-prompt)
            binding.biometrics.post(() -> binding.biometrics.performClick());
        } else {
            // Рус: Если биометрия недоступна - скрываем кнопку
            // Eng: If biometrics unavailable - hide button
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    // Рус: Очистка привязки при уничтожении View (предотвращает утечки)
    // Eng: Clear binding when View is destroyed (prevents leaks)
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
