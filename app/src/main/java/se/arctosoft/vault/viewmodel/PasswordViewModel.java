package se.arctosoft.vault.viewmodel;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModel;

import java.io.InputStream;

import se.arctosoft.vault.data.DirHash;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.encryption.Encryption;
import se.arctosoft.vault.exception.InvalidPasswordException;

// EN: ViewModel to manage password state and vault initialization
// RU: ViewModel для управления состоянием пароля и инициализации хранилища
public class PasswordViewModel extends ViewModel {
    private static final String TAG = "PasswordViewModel";

    private Password password;
    private Uri selectedFolderUri; // EN: Store selected vault path / RU: Храним путь к выбранному сейфу

    public boolean isLocked() {
        initPassword();
        return password.getPassword() == null;
    }

    private void initPassword() {
        if (password == null) {
            this.password = Password.getInstance();
        }
    }

    public void setPassword(char[] password) {
        initPassword();
        this.password.setPassword(password);
    }

    public char[] getPassword() {
        initPassword();
        return password.getPassword();
    }

    public void setDirHash(DirHash dirHash) {
        initPassword();
        this.password.setDirHash(dirHash);
    }

    // EN: Getter for the selected folder / RU: Геттер для выбранной папки
    public Uri getSelectedFolderUri() {
        return selectedFolderUri;
    }

    /**
     * EN: Validates password by attempting to decrypt the first found .valv file.
     * RU: Проверяет пароль, пытаясь расшифровать первый найденный .valv файл.
     */
    public boolean initializeVault(Context context, Uri folderUri, char[] passwordInput) {
        initPassword();
        this.selectedFolderUri = folderUri; // EN: Save the URI / RU: Сохраняем URI
        
        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, folderUri);
            if (root == null || !root.isDirectory()) return false;

            // EN: Find a .valv file to verify credentials / RU: Ищем .valv файл для проверки
            DocumentFile verificationFile = null;
            for (DocumentFile file : root.listFiles()) {
                if (file.getName() != null && file.getName().endsWith(".valv")) {
                    verificationFile = file;
                    break;
                }
            }

            // EN: If vault is empty, we accept the password as new / RU: Если сейф пуст, принимаем пароль как новый
            if (verificationFile == null) {
                this.setPassword(passwordInput);
                return true;
            }

            // EN: Try to open the encrypted stream / RU: Пробуем открыть зашифрованный поток
            try (InputStream is = context.getContentResolver().openInputStream(verificationFile.getUri())) {
                // EN: We use V2 as a safe default for verification
                // RU: Используем V2 как стандарт для проверки
                Encryption.Streams streams = Encryption.getCipherInputStream(is, passwordInput, false, 2);
                streams.close();

                // EN: If no exception, password is correct / RU: Если нет ошибок, пароль верный
                this.setPassword(passwordInput);
                return true;
            } catch (InvalidPasswordException e) {
                Log.e(TAG, "Invalid password entered");
                this.selectedFolderUri = null; // EN: Reset on failure / RU: Сброс при ошибке
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Decryption check failed", e);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Vault init failed", e);
            return false;
        }
    }
}
