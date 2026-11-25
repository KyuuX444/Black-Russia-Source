package com.ryzn.launcher.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.ryzn.game.R;
import com.ryzn.launcher.other.RyznGameDownloader;

public class LoaderActivity extends AppCompatActivity {

    private RyznGameDownloader downloader;
    private RoundCornerProgressBar progressBar;
    private TextView percentText;
    private TextView statusText;
    private boolean downloadStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_loader);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        progressBar = findViewById(R.id.progress_bar);
        percentText = findViewById(R.id.loading_percent);
        statusText = findViewById(R.id.loading_text);
        downloader = new RyznGameDownloader();

        if (RyznGameDownloader.hasStoragePermission(this)) {
            beginDownload();
        } else {
            if (statusText != null) {
                statusText.setText("Menunggu izin akses penyimpanan...");
            }
            RyznGameDownloader.requestAllFilesPermissionIfNeeded(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!downloadStarted && RyznGameDownloader.hasStoragePermission(this)) {
            beginDownload();
        }
    }

    private void beginDownload() {
        if (downloadStarted) return;
        downloadStarted = true;
        downloader.startDownload(this, progressBar, percentText, statusText, new RyznGameDownloader.Callback() {
            @Override
            public void onSuccess() {
                Toast.makeText(LoaderActivity.this, "Data Ryzn SA-MP berhasil dipasang!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoaderActivity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(String message) {
                downloadStarted = false;
                Toast.makeText(LoaderActivity.this, "Gagal mengunduh data game: " + message, Toast.LENGTH_LONG).show();
                startActivity(new Intent(LoaderActivity.this, MainActivity.class));
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloader != null) {
            downloader.cancel();
        }
    }
}
