package com.ryzn.launcher.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.ryzn.game.R;
import com.ryzn.launcher.fragment.FavoriteServersFragment;
import com.ryzn.launcher.fragment.HomeFragment;
import com.ryzn.launcher.fragment.SettingsFragment;
import com.ryzn.launcher.other.RyznGameDownloader;
import com.ryzn.launcher.other.ServerManager;

/**
 * Portrait 9:16 launcher host managing the top Header, Fragment navigation (Home, Servers, Settings),
 * runtime storage/audio permissions, and live Footer client readiness status.
 */
public class MainActivity extends AppCompatActivity {

    public static final int TAB_HOME = 1;
    public static final int TAB_SERVERS = 2;
    public static final int TAB_SETTINGS = 3;

    private int selectedTab = TAB_HOME;
    private long quitTime = 0;

    private ImageButton navHomeBtn;
    private ImageButton navServersBtn;
    private ImageButton navSettingsBtn;
    private View footerStatusDot;
    private TextView footerStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestRequiredPermissions();

        navHomeBtn = findViewById(R.id.header_nav_home);
        navServersBtn = findViewById(R.id.header_nav_servers);
        navSettingsBtn = findViewById(R.id.header_nav_settings);
        footerStatusDot = findViewById(R.id.footer_status_dot);
        footerStatusText = findViewById(R.id.footer_status_text);

        bindVersionLabels();
        bindHeaderAndFooterActions();

        if (savedInstanceState == null) {
            switchTab(TAB_HOME, new HomeFragment());
        } else {
            updateHeaderNavHighlight();
        }
        refreshFooterClientState();
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
        TextView footerBrand = findViewById(R.id.footer_brand_version);
        if (headerVersion != null) {
            headerVersion.setText("v" + version);
        }
        if (footerBrand != null) {
            footerBrand.setText("Ryzn SA-MP • v" + version);
        }
    }

    private void bindHeaderAndFooterActions() {
        navHomeBtn.setOnClickListener(v -> openHomeTab());
        navServersBtn.setOnClickListener(v -> openServersTab());
        navSettingsBtn.setOnClickListener(v -> openSettingsTab());

        findViewById(R.id.footer_client_status).setOnClickListener(v -> {
            ServerManager.ClientState state = ServerManager.getClientState(this);
            if (state == ServerManager.ClientState.NEEDS_PERMISSION) {
                requestRequiredPermissions();
            } else if (state == ServerManager.ClientState.NEEDS_DOWNLOAD) {
                startActivity(new Intent(this, LoaderActivity.class));
            } else {
                Toast.makeText(this, "Client siap di /storage/emulated/0/" + RyznGameDownloader.GAME_DIR_NAME + "/", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void openHomeTab() {
        if (selectedTab != TAB_HOME) {
            switchTab(TAB_HOME, new HomeFragment());
        }
    }

    public void openServersTab() {
        if (selectedTab != TAB_SERVERS) {
            switchTab(TAB_SERVERS, new FavoriteServersFragment());
        }
    }

    public void openSettingsTab() {
        if (selectedTab != TAB_SETTINGS) {
            switchTab(TAB_SETTINGS, new SettingsFragment());
        }
    }

    private void switchTab(int targetTab, Fragment fragment) {
        selectedTab = targetTab;
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit();
        updateHeaderNavHighlight();
    }

    private void updateHeaderNavHighlight() {
        if (navHomeBtn != null) {
            navHomeBtn.setBackgroundResource(selectedTab == TAB_HOME
                    ? R.drawable.bg_nav_item_selected
                    : R.drawable.bg_nav_item_normal);
        }
        if (navServersBtn != null) {
            navServersBtn.setBackgroundResource(selectedTab == TAB_SERVERS
                    ? R.drawable.bg_nav_item_selected
                    : R.drawable.bg_nav_item_normal);
        }
        if (navSettingsBtn != null) {
            navSettingsBtn.setBackgroundResource(selectedTab == TAB_SETTINGS
                    ? R.drawable.bg_nav_item_selected
                    : R.drawable.bg_nav_item_normal);
        }
    }

    public void refreshFooterClientState() {
        if (footerStatusDot == null || footerStatusText == null) return;
        ServerManager.ClientState state = ServerManager.getClientState(this);
        if (state == ServerManager.ClientState.READY) {
            footerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            footerStatusText.setText("Client Siap");
        } else if (state == ServerManager.ClientState.NEEDS_PERMISSION) {
            footerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            footerStatusText.setText("Butuh Izin File");
        } else {
            footerStatusDot.setBackgroundResource(R.drawable.bg_status_dot_pending);
            footerStatusText.setText("Unduh Data Game");
        }
    }

    public void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Tentang Ryzn SA-MP")
                .setMessage("Ryzn SA-MP Mobile Launcher\n"
                        + "Versi: v" + getAppVersionName() + "\n"
                        + "Direktori Data: /storage/emulated/0/" + RyznGameDownloader.GAME_DIR_NAME + "/\n"
                        + "Skin Dialog: SAMP/sampgui.png")
                .setPositiveButton("Tutup", null)
                .show();
    }

    public void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar Launcher")
                .setMessage("Tutup Ryzn SA-MP sekarang?")
                .setPositiveButton("Keluar", (d, w) -> finish())
                .setNegativeButton("Batal", null)
                .show();
    }

    private String getAppVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null && !info.versionName.trim().isEmpty()) {
                return info.versionName.trim();
            }
        } catch (Exception ignored) {
        }
        return "1.0-ryzn";
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
