package se.arctosoft.vault;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import se.arctosoft.vault.databinding.FragmentStartBinding;
import se.arctosoft.vault.utils.ViewAnimations;

public class StartFragment extends Fragment {
    private FragmentStartBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // EN: Setup logo animation / RU: Настройка анимации логотипа
        ViewAnimations.setupElasticLogo(binding.headerArea, binding.ivLogo);

        // EN: Navigate to open existing vault / RU: Переход для открытия существующего хранилища
        binding.btnOpenVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", false);
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });

        // EN: Navigate to create new vault / RU: Переход для создания нового хранилища
        binding.btnCreateVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", true);
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
