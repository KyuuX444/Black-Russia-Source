package com.byparad1st.launcher.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.byparad1st.game.R;
import com.byparad1st.launcher.fragment.FavoriteServersFragment;
import com.byparad1st.launcher.fragment.SettingsPageFragment;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

public class MainActivity extends AppCompatActivity {

    private int selectedTab = 1;
    private long quitTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 30) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1000);
            }
            if (!AlyaLiteDownloader.hasStoragePermission(this)) {
                AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(this);
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

        final LinearLayout playLayout = findViewById(R.id.playLayout);
        final LinearLayout settingsLayout = findViewById(R.id.settingsLayout);
        final ImageView playImage = findViewById(R.id.playImage);
        final ImageView settingsImage = findViewById(R.id.settingsImage);
        final TextView playTxt = findViewById(R.id.playTxt);
        final TextView settingsTxt = findViewById(R.id.settingsTxt);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, new FavoriteServersFragment())
                    .commit();
        }

        playLayout.setOnClickListener(v -> {
            if (selectedTab != 1) {
                switchTab(1, new FavoriteServersFragment(),
                        playLayout, playImage, playTxt,
                        settingsLayout, settingsImage, settingsTxt);
            }
        });

        settingsLayout.setOnClickListener(v -> {
            if (selectedTab != 2) {
                switchTab(2, new SettingsPageFragment(),
                        playLayout, playImage, playTxt,
                        settingsLayout, settingsImage, settingsTxt);
            }
        });
    }

    private void switchTab(
            int targetTab,
            Fragment fragment,
            LinearLayout playLayout, ImageView playImage, TextView playTxt,
            LinearLayout settingsLayout, ImageView settingsImage, TextView settingsTxt
    ) {
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment)
                .commit();

        if (targetTab == 1) {
            settingsTxt.setVisibility(View.GONE);
            settingsImage.setImageResource(R.drawable.settings_icon);
            settingsLayout.setBackgroundColor(Color.TRANSPARENT);

            playTxt.setVisibility(View.VISIBLE);
            playImage.setImageResource(R.drawable.play_selected_icon);
            playLayout.setBackgroundResource(R.drawable.alyn_nav_pill);

            ScaleAnimation anim = new ScaleAnimation(0.85f, 1.0f, 1.0f, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            anim.setDuration(180);
            playLayout.startAnimation(anim);
        } else {
            playTxt.setVisibility(View.GONE);
            playImage.setImageResource(R.drawable.play_icon);
            playLayout.setBackgroundColor(Color.TRANSPARENT);

            settingsTxt.setVisibility(View.VISIBLE);
            settingsImage.setImageResource(R.drawable.settings_selected_icon);
            settingsLayout.setBackgroundResource(R.drawable.alyn_nav_pill);

            ScaleAnimation anim = new ScaleAnimation(0.85f, 1.0f, 1.0f, 1.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            anim.setDuration(180);
            settingsLayout.startAnimation(anim);
        }
        selectedTab = targetTab;
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - quitTime > 2000) {
            Toast.makeText(this, "Tekan sekali lagi untuk keluar.", Toast.LENGTH_SHORT).show();
            quitTime = System.currentTimeMillis();
        } else {
            super.onBackPressed();
            finish();
        }
    }
}
