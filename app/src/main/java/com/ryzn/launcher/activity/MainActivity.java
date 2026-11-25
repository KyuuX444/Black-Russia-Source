package com.ryzn.launcher.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.ryzn.game.R;
import com.ryzn.launcher.fragment.HomeFragment;
import com.ryzn.launcher.fragment.SettingsFragment;
import com.ryzn.launcher.other.MangaUiHelper;
import com.ryzn.launcher.other.RyznGameDownloader;
import com.ryzn.launcher.other.ServerManager;

/**
 * Portrait Manga-themed launcher host with 2 bottom navigation buttons (Beranda & Pengaturan),
 * runtime storage/audio permissions, and live client readiness status in the header.
 */
public class MainActivity extends AppCompatActivity {

    public static final int TAB_HOME = 1;
    public static final int TAB_SETTINGS = 2;
    private static final String STATE_SELECTED_TAB = "selected_tab";

    private int selectedTab = TAB_HOME;
    private long quitTime = 0;

    private View navHomeBtn;
    private View navSettingsBtn;
    private ImageView navHomeIcon;
    private ImageView navSettingsIcon;
    private TextView navHomeText;
    private TextView navSettingsText;

    private View statusDot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRequiredPermissions();

        navHomeBtn = findViewById(R.id.bottom_nav_home);
        navSettingsBtn = findViewById(R.id.bottom_nav_settings);
        navHomeIcon = findViewById(R.id.bottom_nav_home_icon);
        navSettingsIcon = findViewById(R.id.bottom_nav_settings_icon);
        navHomeText = findViewById(R.id.bottom_nav_home_text);
        navSettingsText = findViewById(R.id.bottom_nav_settings_text);

        statusDot = findViewById(R.id.footer_status_dot);
        statusText = findViewById(R.id.footer_status_text);

        bindVersionLabels();
        bindActions();

        if (savedInstanceState == null) {
            switchTab(TAB_HOME, new HomeFragment());
        } else {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, TAB_HOME);
            updateBottomNavHighlight();
        }
        refreshFooterClientState();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, selectedTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFooterClientState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshFooterClientState();
    }

    public void requestStorageOrAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            RyznGameDownloader.requestAllFilesPermissionIfNeeded(this);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            }, 1000);
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1000);
            }
            if (!RyznGameDownloader.hasStoragePermission(this)) {
                RyznGameDownloader.requestAllFilesPermissionIfNeeded(this);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO
                }, 1000);
            }
        }
    }

    private void bindVersionLabels() {
        String version = getAppVersionName();
        TextView headerVersion = findViewById(R.id.header_version);
        if (headerVersion != null) {
            headerVersion.setText("v" + version);
        }
    }

    private void bindActions() {
        View footerStatusPill = findViewById(R.id.footer_client_status);
        MangaUiHelper.attachPressFeedback(navHomeBtn, navSettingsBtn, footerStatusPill);

        navHomeBtn.setOnClickListener(v -> openHomeTab());
        navSettingsBtn.setOnClickListener(v -> openSettingsTab());

        footerStatusPill.setOnClickListener(v -> {
            ServerManager.ClientState state = ServerManager.getClientState(this);
            if (state == ServerManager.ClientState.NEEDS_PERMISSION) {
                requestRequiredPermissions();
            } else if (state == ServerManager.ClientState.NEEDS_DOWNLOAD) {
                startActivity(new Intent(this, LoaderActivity.class));
            } else {
                String msg = RyznGameDownloader.isModifiedDataEnabled(this)
                        ? "Mode Modified Data aktif: membaca data game yang ada"
                        : "Client siap di /storage/emulated/0/" + RyznGameDownloader.GAME_DIR_NAME + "/";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void openHomeTab() {
        if (selectedTab != TAB_HOME) {
            switchTab(TAB_HOME, new HomeFragment());
        }
    }

    public void openSettingsTab() {
        if (selectedTab != TAB_SETTINGS) {
            switchTab(TAB_SETTINGS, new SettingsFragment());
        }
    }

    private void switchTab(int targetTab, Fragment fragment) {
        int prevTab = selectedTab;
        selectedTab = targetTab;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction().setReorderingAllowed(true);
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) != null && prevTab != targetTab) {
            if (targetTab > prevTab) {
                tx.setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit);
            } else {
                tx.setCustomAnimations(R.anim.tab_pop_enter, R.anim.tab_pop_exit);
            }
        }
        tx.replace(R.id.fragment_container, fragment).commit();
        updateBottomNavHighlight();
    }

    private void updateBottomNavHighlight() {
        int white = ContextCompat.getColor(this, R.color.white);
        int ink = ContextCompat.getColor(this, R.color.manga_ink);

        boolean isHome = selectedTab == TAB_HOME;
        if (navHomeBtn != null) {
            navHomeBtn.setBackgroundResource(isHome ? R.drawable.bg_nav_item_selected : R.drawable.bg_nav_item_normal);
            if (navHomeIcon != null) {
                ImageViewCompat.setImageTintList(navHomeIcon, ColorStateList.valueOf(isHome ? white : ink));
            }
            if (navHomeText != null) {
                navHomeText.setTextColor(isHome ? white : ink);
            }
        }
        if (navSettingsBtn != null) {
            navSettingsBtn.setBackgroundResource(!isHome ? R.drawable.bg_nav_item_selected : R.drawable.bg_nav_item_normal);
            if (navSettingsIcon != null) {
                ImageViewCompat.setImageTintList(navSettingsIcon, ColorStateList.valueOf(!isHome ? white : ink));
            }
            if (navSettingsText != null) {
                navSettingsText.setTextColor(!isHome ? white : ink);
            }
        }
    }

    public void refreshFooterClientState() {
        if (statusDot == null || statusText == null) return;
        ServerManager.ClientState state = ServerManager.getClientState(this);
        if (state == ServerManager.ClientState.READY) {
            statusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            statusText.setText(RyznGameDownloader.isModifiedDataEnabled(this) ? "Client Siap (Mod)" : "Client Siap");
        } else if (state == ServerManager.ClientState.NEEDS_PERMISSION) {
            statusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            statusText.setText("Butuh Izin");
        } else {
            statusDot.setBackgroundResource(R.drawable.bg_status_dot_pending);
            statusText.setText("Unduh Data");
        }
    }

    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null && !info.versionName.trim().isEmpty()) {
                return info.versionName.trim();
            }
        } catch (Exception ignored) {
        }
        return "2.0-ryzn";
    }

    @Override
    public void onBackPressed() {
        if (selectedTab != TAB_HOME) {
            openHomeTab();
            return;
        }
        if (System.currentTimeMillis() - quitTime > 2000) {
            Toast.makeText(this, "Tekan sekali lagi untuk keluar.", Toast.LENGTH_SHORT).show();
            quitTime = System.currentTimeMillis();
        } else {
            super.onBackPressed();
            finish();
        }
    }
}
