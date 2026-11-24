package com.byparad1st.launcher.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.byparad1st.game.R;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

public class LoaderActivity extends AppCompatActivity {

    private AlyaLiteDownloader downloader;
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
        downloader = new AlyaLiteDownloader();

        if (AlyaLiteDownloader.hasStoragePermission(this)) {
            beginDownload();
        } else {
            if (statusText != null) {
                statusText.setText("Menunggu izin akses penyimpanan...");
            }
            AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!downloadStarted && AlyaLiteDownloader.hasStoragePermission(this)) {
            beginDownload();
        }
    }

    private void beginDownload() {
        if (downloadStarted) return;
        downloadStarted = true;
        downloader.startDownload(this, progressBar, percentText, statusText, new AlyaLiteDownloader.Callback() {
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
