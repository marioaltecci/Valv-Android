package se.arctosoft.vault;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
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
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public class PasswordFragment extends Fragment {
    private static final String TAG = "PasswordFragment";
    public static String LOGIN_SUCCESSFUL = "LOGIN_SUCCESSFUL";

    private PasswordViewModel passwordViewModel;
    private SavedStateHandle savedStateHandle;
    private FragmentPasswordBinding binding;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    // Для эффекта резины
    private float lastTouchY;
    private final float RESISTANCE = 0.15f; 
    private final float MAX_DRAG = 30f; 

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

        // --- ЛОГИКА ТРЯСКИ И РАЗТЯГИВАНИЯ ЛОГОТИПА ---
        binding.logoContainer.setOnClickListener(v -> shakeView(binding.ivLogo));

        binding.logoContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaY = event.getRawY() - lastTouchY;
                    if (deltaY > 0) {
                        float drag = Math.min(deltaY * RESISTANCE, MAX_DRAG);
                        v.setTranslationY(drag);
                        v.setScaleY(1f + (drag * 0.002f));
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float currentY = v.getTranslationY();
                    v.animate().translationY(0).scaleY(1f)
                            .setInterpolator(new OvershootInterpolator(4f))
                            .setDuration(400).start();
                    if (currentY < 15) v.performClick();
                    break;
            }
            return true;
        });

        binding.btnUnlock.setEnabled(false);

        binding.eTPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                int length = (s != null) ? s.length() : 0;
                if (length > 60) {
                    if (binding.textField.getError() == null) {
                        binding.textField.setError("max 60");
                        binding.textField.setErrorEnabled(true);
                        shakeView(binding.textField);
                    }
                    binding.btnUnlock.setEnabled(false);
                } else {
                    if (binding.textField.getError() != null) {
                        binding.textField.setError(null);
                        binding.textField.setErrorEnabled(false);
                    }
                    binding.btnUnlock.setEnabled(length > 0);
                }
            }
        });

        binding.eTPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                if (binding.btnUnlock.setEnabled()) {
                    binding.btnUnlock.performClick();
                }
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
                    if (dirHash == null) {
                        byte[] salt = Encryption.generateSecureSalt(Encryption.SALT_LENGTH);
                        dirHash = Encryption.getDirHash(salt, temp);
                        if (dirHash != null) {
                            settings.createDirHashEntry(salt, dirHash.hash());
                        } else {
                            throw new Exception("Не удалось создать хэш директории");
                        }
                    }

                    DirHash finalDirHash = dirHash;
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            passwordViewModel.setDirHash(finalDirHash);
                            binding.eTPassword.setText(null);
                            MainActivity.GLIDE_KEY = System.currentTimeMillis();
                            savedStateHandle.set(LOGIN_SUCCESSFUL, true);
                            NavHostFragment.findNavController(this).popBackStack();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Критическая ошибка разблокировки", e);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            int currentLen = binding.eTPassword.length();
                            binding.btnUnlock.setEnabled(currentLen > 0 && currentLen <= 60);
                            binding.eTPassword.setEnabled(true);
                            binding.biometrics.setEnabled(true);
                            binding.loading.setVisibility(View.GONE);
                            Toaster.getInstance(requireActivity()).showShort("Ошибка: " + e.getMessage());
                        });
                    }
                }
            }).start();
        });

        binding.btnHelp.setOnClickListener(v -> Dialogs.showTextDialog(requireContext(), null, getString(R.string.launcher_help_message)));

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
                            Log.e(TAG, "Decrypt error", e);
                            Toaster.getInstance(requireActivity()).showShort("Ошибка биометрии");
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
                    if (iv == null) throw new Exception("IV не найден");
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                    biometricPrompt.authenticate(promptInfo, new BiometricPrompt.CryptoObject(cipher));
                } catch (Exception e) {
                    Toaster.getInstance(requireContext()).showShort("Биометрия недоступна");
                }
            });

            binding.biometrics.post(() -> binding.biometrics.performClick());
        } else {
            binding.biometrics.setVisibility(View.GONE);
        }
    }

    private void shakeView(View view) {
        ObjectAnimator shaker = ObjectAnimator.ofFloat(view, "translationX", 0, 20, -20, 20, -20, 15, -15, 0);
        shaker.setDuration(400);
        shaker.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
                        }
