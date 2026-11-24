package com.byparad1st.launcher.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.byparad1st.game.R;
import com.byparad1st.launcher.activity.MainActivity;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

public class LoaderFragment extends Fragment {

    private AlyaLiteDownloader downloader;
    private RoundCornerProgressBar progressBar;
    private TextView percentText;
    private TextView statusText;
    private boolean downloadStarted = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflate = inflater.inflate(R.layout.fragment_loader, container, false);
        progressBar = inflate.findViewById(R.id.progress_bar);
        percentText = inflate.findViewById(R.id.loading_percent);
        statusText = inflate.findViewById(R.id.loading_text);
        downloader = new AlyaLiteDownloader();

        Activity act = getActivity();
        if (act != null && AlyaLiteDownloader.hasStoragePermission(act)) {
            beginDownload();
        } else if (act != null) {
            if (statusText != null) {
                statusText.setText("Menunggu izin akses penyimpanan...");
            }
            AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(act);
        }
        return inflate;
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity act = getActivity();
        if (!downloadStarted && act != null && AlyaLiteDownloader.hasStoragePermission(act)) {
            beginDownload();
        }
    }

    private void beginDownload() {
        final Activity act = getActivity();
        if (act == null || downloadStarted) return;
        downloadStarted = true;
        downloader.startDownload(act, progressBar, percentText, statusText, new AlyaLiteDownloader.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Toast.makeText(act, "Data Lite ryzn SA-MP berhasil dipasang!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(act, MainActivity.class));
                act.finish();
            }

            @Override
            public void onError(String message) {
                downloadStarted = false;
                if (!isAdded()) return;
                Toast.makeText(act, "Gagal mengunduh data game: " + message, Toast.LENGTH_LONG).show();
                startActivity(new Intent(act, MainActivity.class));
                act.finish();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloader != null) {
            downloader.cancel();
        }
    }
}
