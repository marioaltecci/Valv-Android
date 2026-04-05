package se.arctosoft.vault;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import se.arctosoft.vault.databinding.FragmentStartBinding;

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

        // EN: Navigate to password with "Open" mode / RU: Переход к паролю в режиме "Открыть"
        binding.btnOpenVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", false);
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });

        // EN: Navigate to password with "Create" mode / RU: Переход к паролю в режиме "Создать"
        binding.btnCreateVault.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("is_create_mode", true);
            Navigation.findNavController(v).navigate(R.id.action_start_to_password, args);
        });
    }
}
