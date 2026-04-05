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
        // EN: Inflate the layout using view binding / RU: Инфлейтим разметку через view binding
        binding = FragmentStartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // EN: Setup logo animations for One UI consistency / RU: Настройка анимации логотипа для единообразия One UI
        ViewAnimations.setupElasticLogo(binding.headerArea, binding.ivLogo);

        // EN: Open existing vault action / RU: Действие для открытия существующего хранилища
        binding.btnOpenVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", false); // EN: Set mode to Open / RU: Режим "Открыть"
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });

        // EN: Create new vault action / RU: Действие для создания нового хранилища
        binding.btnCreateVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", true); // EN: Set mode to Create / RU: Режим "Создать"
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // EN: Clean up binding to prevent memory leaks / RU: Очищаем binding для предотвращения утечек памяти
        binding = null;
    }
}
