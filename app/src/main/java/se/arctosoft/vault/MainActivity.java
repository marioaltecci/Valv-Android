package se.arctosoft.vault;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.ArrayList;
import java.util.List;

import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.ActivityMainBinding;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.viewmodel.ShareViewModel;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static long GLIDE_KEY = System.currentTimeMillis();

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // EN: Enable modern edge-to-edge support
        // RU: Включаем современную поддержку отображения "от края до края"
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        
        // EN: Explicitly tell the window not to fit system windows to allow full-screen backgrounds
        // RU: Явно говорим окну не подгонять контент под системные бары для полноэкранных фонов
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        Settings settings = Settings.getInstance(this);
        
        // Use secure flag to protect sensitive content
        // Используем флаг безопасности для защиты контента
        if (settings.isSecureFlag()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Manage Toolbar visibility and ensure clean transitions
        // Управление видимостью тулбара и обеспечение чистых переходов
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (destination.getId() == R.id.password) {
                // EN: Hide app bar for password screen to allow image to hit the top
                // RU: Прячем апп-бар для экрана пароля, чтобы картинка доходила до верха
                binding.appBar.setVisibility(View.GONE);
            } else {
                binding.appBar.setVisibility(View.VISIBLE);
            }
        });

        handleIntents(getIntent());
    }

    // --- INTENT HANDLING (FOR SHARING FILES) ---
    // --- ОБРАБОТКА ИНТЕНТОВ (ДЛЯ ПРИЕМА ФАЙЛОВ) ---

    private void handleIntents(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (type != null && Intent.ACTION_SEND.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/")) {
                handleSendSingle(intent);
            }
        } else if (type != null && Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (type.startsWith("image/") || type.startsWith("video/") || type.equals("*/*")) {
                handleSendMultiple(intent);
            }
        }
    }

    private void handleSendSingle(@NonNull Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            List<Uri> list = new ArrayList<>(1);
            list.add(uri);
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, list);
            if (!documentFiles.isEmpty()) {
                addSharedFiles(documentFiles);
            }
        }
    }

    private void handleSendMultiple(@NonNull Intent intent) {
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (uris != null) {
            List<DocumentFile> documentFiles = FileStuff.getDocumentsFromShareIntent(this, uris);
            if (!documentFiles.isEmpty()) {
                addSharedFiles(documentFiles);
            }
        }
    }

    private void addSharedFiles(@NonNull List<DocumentFile> documentFiles) {
        Log.e(TAG, "Files received via share: " + documentFiles.size());
        ShareViewModel shareViewModel = new ViewModelProvider(this).get(ShareViewModel.class);
        shareViewModel.clear();
        shareViewModel.getFilesReceived().addAll(documentFiles);
        shareViewModel.setHasData(true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        // Lock the vault if the app is destroyed (and not just rotating)
        // Блокируем сейф, если приложение закрывается (а не просто переворачивается экран)
        if (!isChangingConfigurations()) {
            Password.lock(this, false);
        }
        super.onDestroy();
    }
}
