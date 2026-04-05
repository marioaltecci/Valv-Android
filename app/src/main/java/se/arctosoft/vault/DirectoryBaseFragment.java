/*
 * Valv-Android
 * Copyright (c) 2024 Arctosoft AB.
 */

package se.arctosoft.vault;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import se.arctosoft.vault.adapters.GalleryGridAdapter;
import se.arctosoft.vault.adapters.GalleryPagerAdapter;
import se.arctosoft.vault.data.FileType;
import se.arctosoft.vault.data.GalleryFile;
import se.arctosoft.vault.data.Password;
import se.arctosoft.vault.databinding.FragmentDirectoryBinding;
import se.arctosoft.vault.utils.Dialogs;
import se.arctosoft.vault.utils.FileStuff;
import se.arctosoft.vault.utils.Settings;
import se.arctosoft.vault.utils.Toaster;
import se.arctosoft.vault.viewmodel.CopyViewModel;
import se.arctosoft.vault.viewmodel.DeleteViewModel;
import se.arctosoft.vault.viewmodel.ExportViewModel;
import se.arctosoft.vault.viewmodel.GalleryViewModel;
import se.arctosoft.vault.viewmodel.ImportViewModel;
import se.arctosoft.vault.viewmodel.MoveViewModel;
import se.arctosoft.vault.viewmodel.PasswordViewModel;

public abstract class DirectoryBaseFragment extends Fragment implements MenuProvider {
    private static final String TAG = "DirectoryBaseFragment";

    static final Object LOCK = new Object();
    static final int MIN_FILES_FOR_FAST_SCROLL = 60;
    static final int ORDER_BY_NEWEST = 0;
    static final int ORDER_BY_OLDEST = 1;
    static final int ORDER_BY_LARGEST = 2;
    static final int ORDER_BY_SMALLEST = 3;
    static final int ORDER_BY_RANDOM = 4;
    static final int FILTER_ALL = 0;
    static final int FILTER_IMAGES = FileType.IMAGE_V2.type;
    static final int FILTER_GIFS = FileType.GIF_V2.type;
    static final int FILTER_VIDEOS = FileType.VIDEO_V2.type;
    static final int FILTER_TEXTS = FileType.TEXT_V2.type;

    NavController navController;
    FragmentDirectoryBinding binding;
    PasswordViewModel passwordViewModel;
    GalleryViewModel galleryViewModel;
    ImportViewModel importViewModel;
    DeleteViewModel deleteViewModel;
    ExportViewModel exportViewModel;
    CopyViewModel copyViewModel;
    MoveViewModel moveViewModel;

    GalleryGridAdapter galleryGridAdapter;
    GalleryPagerAdapter galleryPagerAdapter;
    Settings settings;

    int orderBy = ORDER_BY_NEWEST;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navController = NavHostFragment.findNavController(this);
        NavBackStackEntry navBackStackEntry = navController.getCurrentBackStackEntry();
        SavedStateHandle savedStateHandle = navBackStackEntry.getSavedStateHandle();
        savedStateHandle.getLiveData(PasswordFragment.LOGIN_SUCCESSFUL).observe(navBackStackEntry, o -> {
            if (!(Boolean) o) {
                Password.lock(getContext(), false);
                if (getActivity() != null) getActivity().finish();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDirectoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        passwordViewModel = new ViewModelProvider(requireActivity()).get(PasswordViewModel.class);
        galleryViewModel = new ViewModelProvider(this).get(GalleryViewModel.class);
        importViewModel = new ViewModelProvider(this).get(ImportViewModel.class);
        deleteViewModel = new ViewModelProvider(this).get(DeleteViewModel.class);
        exportViewModel = new ViewModelProvider(this).get(ExportViewModel.class);
        copyViewModel = new ViewModelProvider(this).get(CopyViewModel.class);
        moveViewModel = new ViewModelProvider(this).get(MoveViewModel.class);
        
        // EN: UI initialization / RU: Инициализация интерфейса
        init();
        setPadding();
    }

    private void setPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutFabsAdd, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public abstract void init();

    boolean initActionBar(boolean isAllFolder) {
        ActionBar ab = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            String title = isAllFolder ? getString(R.string.gallery_all) : FileStuff.getFilenameFromUri(galleryViewModel.getCurrentDirectoryUri(), false);
            
            // EN: Clean primary: prefix / RU: Убираем primary:
            if (title != null && title.contains("primary:")) {
                title = title.replace("primary:", "");
            }
            ab.setTitle(title);
            return true;
        }
        return false;
    }

    void setupGrid() {
        initFastScroll();
        // EN: One UI style grid (2 columns for folders usually looks better)
        // RU: Сетка в стиле One UI (2 колонки для папок обычно выглядят лучше)
        int spanCount = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        RecyclerView.LayoutManager layoutManager = new StaggeredGridLayoutManager(spanCount, RecyclerView.VERTICAL);
        binding.recyclerView.setLayoutManager(layoutManager);
        
        galleryGridAdapter = new GalleryGridAdapter(requireActivity(), galleryViewModel.getGalleryFiles(), settings.showFilenames(), galleryViewModel.isRootDir(), galleryViewModel);
        binding.recyclerView.setAdapter(galleryGridAdapter);

        // EN: Handle click to show One UI style password popup
        // RU: Обработка клика для показа всплывающего окна пароля в стиле One UI
        galleryGridAdapter.setOnFileCLicked(pos -> {
            GalleryFile file = galleryViewModel.getGalleryFiles().get(pos);
            if (file.isDirectory() && galleryViewModel.isRootDir()) {
                // EN: Show popup dialog for password / RU: Показываем диалог ввода пароля
                Dialogs.showPasswordDialog(requireContext(), file, (pass) -> {
                    galleryViewModel.setClickedDirectoryUri(file.getUri());
                    showViewpager(true, pos, true);
                });
            } else {
                showViewpager(true, pos, true);
            }
        });
    }

    // ... (остальные методы findFilesIn, orderBy, filterBy остаются без изменений)

    void showViewpager(boolean show, int pos, boolean animate) {
        galleryViewModel.setViewpagerVisible(show);
        if (show) {
            binding.viewPager.setVisibility(View.VISIBLE);
            binding.viewPager.setCurrentItem(pos, false);
            if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) 
                ((AppCompatActivity) requireActivity()).getSupportActionBar().hide();
        } else {
            binding.viewPager.setVisibility(View.GONE);
            if (((AppCompatActivity) requireActivity()).getSupportActionBar() != null) 
                ((AppCompatActivity) requireActivity()).getSupportActionBar().show();
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menu.clear();
        menuInflater.inflate(galleryViewModel.isRootDir() ? R.menu.menu_root : R.menu.menu_dir, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.lock) {
            Password.lock(getContext(), true);
            if (getActivity() != null) getActivity().finish();
            return true;
        }
        return false;
    }

    private void initFastScroll() {
        binding.recyclerView.setFastScrollEnabled(galleryViewModel.getGalleryFiles().size() > MIN_FILES_FOR_FAST_SCROLL);
    }

    public abstract void addRootFolders();
    abstract void onSelectionModeChanged(boolean inSelectionMode);
}
