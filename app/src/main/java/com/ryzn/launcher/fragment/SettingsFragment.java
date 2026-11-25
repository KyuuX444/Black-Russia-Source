package com.ryzn.launcher.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.ryzn.game.R;
import com.ryzn.launcher.activity.LoaderActivity;
import com.ryzn.launcher.activity.MainActivity;
import com.ryzn.launcher.other.RyznGameDownloader;
import com.ryzn.launcher.other.ServerManager;

import org.ini4j.Wini;

import java.io.File;

/**
 * Native client settings screen synced directly with SharedPreferences and /ryznSAMP/SAMP/settings.ini.
 */
public class SettingsFragment extends Fragment {

    private EditText nickInput;
    private Button nickSaveBtn;
    private EditText fontSizeInput;
    private Switch displayFpsSwitch;
    private Switch fullscreenSwitch;
    private Switch radarRectSwitch;
    private Switch voiceSwitch;
    private Switch timestampSwitch;
    private TextView chatStringsText;
    private Button chatMinusBtn;
    private Button chatPlusBtn;
    private Button reinstallBtn;
    private Button restoreBtn;
    private SharedPreferences prefs;
    private boolean bindingValues = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE);

        nickInput = view.findViewById(R.id.settings_nick_input);
        nickSaveBtn = view.findViewById(R.id.settings_nick_save);
        fontSizeInput = view.findViewById(R.id.fontsize);
        displayFpsSwitch = view.findViewById(R.id.displayfps_switch);
        fullscreenSwitch = view.findViewById(R.id.fullscreen_switch);
        radarRectSwitch = view.findViewById(R.id.radarrect_switch);
        voiceSwitch = view.findViewById(R.id.voice_switch);
        timestampSwitch = view.findViewById(R.id.timestamp_switch);
        chatStringsText = view.findViewById(R.id.chat_strings);
        chatMinusBtn = view.findViewById(R.id.chat_minus);
        chatPlusBtn = view.findViewById(R.id.chat_plus);
        reinstallBtn = view.findViewById(R.id.reinstallGame);
        restoreBtn = view.findViewById(R.id.restore_button);

        loadFromIni();
        setupInfoDialogs(view);
        setupListeners();
        return view;
    }

    private void setupInfoDialogs(View view) {
        view.findViewById(R.id.displayfpsIcon).setOnClickListener(v ->
                showOptionInfo("Tampilkan FPS", "Menampilkan angka FPS bersih gaya mobile di pojok kiri bawah saat bermain."));
        view.findViewById(R.id.fullscreenmodeIcon).setOnClickListener(v ->
                showOptionInfo("Mode Layar Penuh", "Mengaktifkan rendering layar penuh hingga area notch/cutout saat bermain."));
        view.findViewById(R.id.radarrectIcon).setOnClickListener(v ->
                showOptionInfo("Radar Kotak", "Mengubah radar/minimap GTA:SA menjadi kotak (mati = radar bulat standar)."));
        view.findViewById(R.id.voicechatIcon).setOnClickListener(v ->
                showOptionInfo("Voice Chat", "Mengaktifkan tombol mikrofon dan indikator daftar pemain berbicara di dalam game."));
        view.findViewById(R.id.chattimestampIcon).setOnClickListener(v ->
                showOptionInfo("Timestamp Obrolan", "Menambahkan penanda waktu [JJ:MM:DD] di depan setiap pesan obrolan."));
        view.findViewById(R.id.gameDataIcon).setOnClickListener(v ->
                showOptionInfo("Data Game", "Mengunduh ulang atau memperbaiki file data Ryzn SA-MP."));
        view.findViewById(R.id.fontsizeIcon).setOnClickListener(v ->
                showOptionInfo("Ukuran Font", "Mengatur ukuran teks obrolan di dalam game (bawaan: 30.0)."));
        view.findViewById(R.id.chatstringsIcon).setOnClickListener(v ->
                showOptionInfo("Baris Obrolan", "Mengatur tinggi & jumlah baris pesan yang terlihat di kotak chat (5–20)."));
        view.findViewById(R.id.resetIcon).setOnClickListener(v ->
                showOptionInfo("Reset Pengaturan", "Mengembalikan semua pengaturan klien ke nilai awal."));
    }

    private void loadFromIni() {
        bindingValues = true;
        String nick = ServerManager.readSavedNickname(requireContext());
        boolean fps = prefs.getBoolean("display_fps", false);
        boolean full = prefs.getBoolean("fullscreen", false);
        boolean radar = prefs.getBoolean("radar_rect", false);
        boolean voice = prefs.getBoolean("voice_chat", true);
        boolean ts = prefs.getBoolean("timestamp", false);
        int chatLines = prefs.getInt("chat_strings", 8);
        String fontSz = String.valueOf(prefs.getFloat("font_size", 30.0f));

        try {
            File ini = RyznGameDownloader.getSettingsFile();
            if (ini.exists()) {
                Wini w = new Wini(ini);
                String fpsVal = w.get("gui", "fpscounter");
                if (fpsVal != null) fps = "1".equals(fpsVal.trim());
                String cutVal = w.get("gui", "cutout");
                if (cutVal != null) full = "1".equals(cutVal.trim());
                String radarVal = w.get("gui", "radarrect");
                if (radarVal != null) radar = "1".equals(radarVal.trim());
                String voiceVal = w.get("gui", "voicelist");
                if (voiceVal != null) voice = !"0".equals(voiceVal.trim());
                String tsVal = w.get("gui", "timestamp");
                if (tsVal != null) ts = "1".equals(tsVal.trim());
                String linesStr = w.get("gui", "ChatMaxMessages");
                if (linesStr != null) {
                    chatLines = Math.max(5, Math.min(20, Integer.parseInt(linesStr.trim())));
                }
                String fs = w.get("gui", "FontSize");
                if (fs != null && !fs.trim().isEmpty()) fontSz = fs.trim();
            }
        } catch (Exception ignored) {
        }

        nickInput.setText(nick);
        displayFpsSwitch.setChecked(fps);
        fullscreenSwitch.setChecked(full);
        radarRectSwitch.setChecked(radar);
        voiceSwitch.setChecked(voice);
        timestampSwitch.setChecked(ts);
        chatStringsText.setText(String.valueOf(chatLines));
        fontSizeInput.setText(fontSz);
        bindingValues = false;
    }

    private void setupListeners() {
        nickSaveBtn.setOnClickListener(v -> {
            String nick = nickInput.getText().toString().trim();
            if (nick.isEmpty()) {
                Toast.makeText(getActivity(), "Nick_Name tidak boleh kosong.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nick.length() > 24 || nick.contains(" ")) {
                Toast.makeText(getActivity(), "Nick_Name maksimal 24 karakter tanpa spasi.", Toast.LENGTH_SHORT).show();
                return;
            }
            ServerManager.saveNickname(requireContext(), nick);
            Toast.makeText(getActivity(), "Nick_Name disimpan: " + nick, Toast.LENGTH_SHORT).show();
        });

        fontSizeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (bindingValues) return;
                try {
                    float val = Float.parseFloat(s.toString().trim());
                    if (val >= 12.0f && val <= 60.0f) {
                        prefs.edit().putFloat("font_size", val).apply();
                        updateIni("gui", "FontSize", String.valueOf(val));
                    }
                } catch (Exception ignored) {
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        displayFpsSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("display_fps", checked).apply();
            updateIni("gui", "fpscounter", checked ? "1" : "0");
        });

        fullscreenSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("fullscreen", checked).apply();
            updateIni("gui", "cutout", checked ? "1" : "0");
        });

        radarRectSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("radar_rect", checked).apply();
            updateIni("gui", "radarrect", checked ? "1" : "0");
        });

        voiceSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("voice_chat", checked).apply();
            updateIni("gui", "voicelist", checked ? "1" : "0");
        });

        timestampSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("timestamp", checked).apply();
            updateIni("gui", "timestamp", checked ? "1" : "0");
        });

        chatMinusBtn.setOnClickListener(v -> adjustChatStrings(-1));
        chatPlusBtn.setOnClickListener(v -> adjustChatStrings(1));

        reinstallBtn.setOnClickListener(v -> {
            if (getActivity() != null) {
                if (!RyznGameDownloader.hasStoragePermission(getActivity())) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).requestStorageOrAllFilesPermission();
                    } else {
                        RyznGameDownloader.requestAllFilesPermissionIfNeeded(getActivity());
                    }
                    return;
                }
                startActivity(new Intent(getActivity(), LoaderActivity.class));
            }
        });

        restoreBtn.setOnClickListener(v -> new AlertDialog.Builder(requireActivity())
                .setTitle("Konfirmasi")
                .setMessage("Kembalikan semua pengaturan ke default?")
                .setPositiveButton("Ya", (d, w) -> {
                    prefs.edit()
                            .putBoolean("display_fps", false)
                            .putBoolean("fullscreen", false)
                            .putBoolean("radar_rect", false)
                            .putBoolean("voice_chat", true)
                            .putBoolean("timestamp", false)
                            .putInt("chat_strings", 8)
                            .putFloat("font_size", 30.0f)
                            .apply();
                    updateIni("gui", "fpscounter", "0");
                    updateIni("gui", "cutout", "0");
                    updateIni("gui", "radarrect", "0");
                    updateIni("gui", "voicelist", "1");
                    updateIni("gui", "timestamp", "0");
                    updateIni("gui", "ChatMaxMessages", "8");
                    updateIni("gui", "FontSize", "30.0");
                    loadFromIni();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).refreshFooterClientState();
                    }
                    Toast.makeText(getActivity(), "Pengaturan telah dikembalikan ke default.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show());
    }

    private void adjustChatStrings(int delta) {
        int current = prefs.getInt("chat_strings", 8);
        int next = Math.max(5, Math.min(20, current + delta));
        if (next == current) return;
        prefs.edit().putInt("chat_strings", next).apply();
        chatStringsText.setText(String.valueOf(next));
        updateIni("gui", "ChatMaxMessages", String.valueOf(next));
    }

    private void updateIni(String section, String key, String value) {
        Context ctx = getActivity();
        if (ctx == null || !RyznGameDownloader.hasStoragePermission(ctx)) {
            return;
        }
        try {
            File ini = RyznGameDownloader.getSettingsFile();
            if (!ini.exists()) {
                RyznGameDownloader.ensureClientConfigAndFonts(ctx, "");
            }
            if (ini.exists()) {
                Wini w = new Wini(ini);
                w.put(section, key, value);
                w.store();
            }
        } catch (Exception ignored) {
        }
    }

    private void showOptionInfo(String title, String body) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("Oke", null)
                .show();
    }
}
