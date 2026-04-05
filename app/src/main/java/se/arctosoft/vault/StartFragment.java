package se.arctosoft.vault;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import se.arctosoft.vault.databinding.FragmentStartBinding;
import se.arctosoft.vault.utils.ViewAnimations;

public class StartFragment extends Fragment {
    private FragmentStartBinding binding;

    // EN: Launcher for selecting a directory / RU: Лаунчер для выбора папки
    private final ActivityResultLauncher<Intent> openTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        // EN: Persist permissions to access the folder later
                        // RU: Сохраняем права доступа к папке на будущее
                        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        requireContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        
                        goToPasswordScreen(treeUri, true);
                    }
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewAnimations.setupElasticLogo(binding.headerArea, binding.ivLogo);

        // EN: Open existing vault (logic remains the same) / RU: Открыть существующее (логика та же)
        binding.btnOpenVault.setOnClickListener(v -> goToPasswordScreen(null, false));

        // EN: Pick a folder where the app will create encrypted files
        // RU: Выбираем папку, где приложение будет создавать зашифрованные файлы
        binding.btnCreateVault.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            openTreeLauncher.launch(intent);
        });
    }

    private void goToPasswordScreen(Uri folderUri, boolean isCreate) {
        Bundle args = new Bundle();
        args.putBoolean("is_create_mode", isCreate);
        if (folderUri != null) {
            args.putString("file_uri", folderUri.toString());
        }
        Navigation.findNavController(requireView()).navigate(R.id.action_start_to_password, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
