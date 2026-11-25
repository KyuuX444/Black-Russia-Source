package com.byparad1st.launcher.fragment;

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

import com.byparad1st.game.R;
import com.byparad1st.launcher.Registration.reg;
import com.byparad1st.launcher.activity.LoaderActivity;
import com.byparad1st.launcher.other.AlyaLiteDownloader;

import org.ini4j.Wini;

import java.io.File;

public class SettingsPageFragment extends Fragment {

    private EditText nickInput;
    private EditText fontSizeInput;
    private Switch systemKeyboardSwitch;
    private Switch displayFpsSwitch;
    private Switch fullscreenSwitch;
    private Switch voiceSwitch;
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
        View view = inflater.inflate(R.layout.fragment_settings_page, container, false);
        prefs = requireActivity().getSharedPreferences("samp_settings", Context.MODE_PRIVATE);

        nickInput = view.findViewById(R.id.settings_nick_input);
        fontSizeInput = view.findViewById(R.id.fontsize);
        systemKeyboardSwitch = view.findViewById(R.id.systemkeyboard_switch);
        displayFpsSwitch = view.findViewById(R.id.displayfps_switch);
        fullscreenSwitch = view.findViewById(R.id.fullscreen_switch);
        voiceSwitch = view.findViewById(R.id.voice_switch);
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
        view.findViewById(R.id.nicknameIcon).setOnClickListener(v ->
                showOptionInfo("Nama Karakter", "Masukkan Nick_Name SA-MP yang akan digunakan saat masuk ke server."));
        view.findViewById(R.id.systemkeyboardIcon).setOnClickListener(v ->
                showOptionInfo("Keyboard Sistem", "Gunakan keyboard bawaan Android saat mengetik obrolan di dalam game."));
        view.findViewById(R.id.displayfpsIcon).setOnClickListener(v ->
                showOptionInfo("Tampilkan FPS", "Menampilkan penghitung FPS di pojok layar saat bermain."));
        view.findViewById(R.id.fullscreenmodeIcon).setOnClickListener(v ->
                showOptionInfo("Mode Layar Penuh", "Mengaktifkan rendering layar penuh hingga area notch/cutout."));
        view.findViewById(R.id.voicechatIcon).setOnClickListener(v ->
                showOptionInfo("Daftar Voice Chat", "Menampilkan indikator daftar pemain yang berbicara melalui voice chat."));
        view.findViewById(R.id.gameDataIcon).setOnClickListener(v ->
                showOptionInfo("Data Game", "Mengunduh ulang atau memperbaiki file data Ryzn SA-MP."));
        view.findViewById(R.id.fontsizeIcon).setOnClickListener(v ->
                showOptionInfo("Ukuran Font", "Mengatur ukuran font di dalam game (bawaan: 30.0)."));
        view.findViewById(R.id.chatstringsIcon).setOnClickListener(v ->
                showOptionInfo("Baris Obrolan", "Mengatur jumlah baris pesan yang tampil pada kotak chat (5–20)."));
        view.findViewById(R.id.resetIcon).setOnClickListener(v ->
                showOptionInfo("Reset Pengaturan", "Mengembalikan semua pengaturan klien ke nilai awal."));
    }

    private void loadFromIni() {
        bindingValues = true;
        String nick = prefs.getString("nick_name", "");
        boolean sysKb = prefs.getBoolean("system_keyboard", true);
        boolean fps = prefs.getBoolean("display_fps", false);
        boolean full = prefs.getBoolean("fullscreen", false);
        boolean voice = prefs.getBoolean("voice_chat", true);
        int chatLines = prefs.getInt("chat_strings", 8);
        String fontSz = String.valueOf(prefs.getFloat("font_size", 30.0f));

        try {
            File ini = AlyaLiteDownloader.getSettingsFile();
            if (ini.exists()) {
                Wini w = new Wini(ini);
                String n = w.get("client", "name");
                if (n != null && !n.trim().isEmpty()) nick = n.trim();
                sysKb = !"0".equals(w.get("gui", "androidKeyboard"));
                fps = "1".equals(w.get("gui", "fpscounter"));
                full = "1".equals(w.get("gui", "cutout"));
                voice = !"0".equals(w.get("gui", "voicelist"));
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
        systemKeyboardSwitch.setChecked(sysKb);
        displayFpsSwitch.setChecked(fps);
        fullscreenSwitch.setChecked(full);
        voiceSwitch.setChecked(voice);
        chatStringsText.setText(String.valueOf(chatLines));
        fontSizeInput.setText(fontSz);
        bindingValues = false;
    }

    private void setupListeners() {
        nickInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (bindingValues) return;
                String v = s != null ? s.toString().trim() : "";
                prefs.edit().putString("nick_name", v).apply();
                reg.setNick(v);
                updateIni("client", "name", v);
            }
            @Override public void afterTextChanged(Editable s) { }
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

        systemKeyboardSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("system_keyboard", checked).apply();
            updateIni("gui", "androidKeyboard", checked ? "1" : "0");
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

        voiceSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (bindingValues) return;
            prefs.edit().putBoolean("voice_chat", checked).apply();
            updateIni("gui", "voicelist", checked ? "1" : "0");
        });

        chatMinusBtn.setOnClickListener(v -> adjustChatStrings(-1));
        chatPlusBtn.setOnClickListener(v -> adjustChatStrings(1));

        reinstallBtn.setOnClickListener(v -> {
            if (getActivity() != null) {
                if (!AlyaLiteDownloader.hasStoragePermission(getActivity())) {
                    AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(getActivity());
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
                            .putBoolean("system_keyboard", true)
                            .putBoolean("display_fps", false)
                            .putBoolean("fullscreen", false)
                            .putBoolean("voice_chat", true)
                            .putInt("chat_strings", 8)
                            .putFloat("font_size", 30.0f)
                            .apply();
                    updateIni("gui", "androidKeyboard", "1");
                    updateIni("gui", "fpscounter", "0");
                    updateIni("gui", "cutout", "0");
                    updateIni("gui", "voicelist", "1");
                    updateIni("gui", "ChatMaxMessages", "8");
                    updateIni("gui", "FontSize", "30.0");
                    loadFromIni();
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
        if (getActivity() == null || !AlyaLiteDownloader.hasStoragePermission(getActivity())) {
            return;
        }
        try {
            File ini = AlyaLiteDownloader.getSettingsFile();
            if (!ini.exists()) {
                AlyaLiteDownloader.ensureClientConfigAndFonts(nickInput != null ? nickInput.getText().toString() : "Player");
            }
            if (ini.exists()) {
                Wini w = new Wini(ini);
                w.put(section, key, value);
                w.store();
            }
        } catch (Exception ignored) {
        }
    }

    private void showOptionInfo(String title, String message) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Oke", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
