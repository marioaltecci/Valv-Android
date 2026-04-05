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

    // EN: Launcher for system file creation / RU: Лаунчер для системного создания файла
    private final ActivityResultLauncher<Intent> createDocLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        goToPasswordScreen(uri, true);
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

        // EN: Open existing vault / RU: Открыть существующий (тут тоже можно добавить SAF позже)
        binding.btnOpenVault.setOnClickListener(v -> goToPasswordScreen(null, false));

        // EN: Create new vault via System File Picker / RU: Создать новый через системный менеджер
        binding.btnCreateVault.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/octet-stream"); // EN: Binary file / RU: Бинарный файл
            intent.putExtra(Intent.EXTRA_TITLE, "new_vault.vault");
            createDocLauncher.launch(intent);
        });
    }

    private void goToPasswordScreen(Uri fileUri, boolean isCreate) {
        Bundle args = new Bundle();
        args.putBoolean("is_create_mode", isCreate);
        if (fileUri != null) {
            args.putString("file_uri", fileUri.toString());
        }
        Navigation.findNavController(requireView()).navigate(R.id.action_start_to_password, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
