package com.ryzn.launcher.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.launcher.activity.MainActivity;
import com.ryzn.launcher.adapter.ServerAdapter;
import com.ryzn.launcher.model.ServerInfo;
import com.ryzn.launcher.other.MangaUiHelper;
import com.ryzn.launcher.other.RyznGameDownloader;
import com.ryzn.launcher.other.ServerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Primary portrait home screen displaying the Manga Los Santos hero banner,
 * searchable SA-MP server list with +IP/Refresh, selected Server Information, and PLAY CTA.
 */
public class HomeFragment extends Fragment {

    private static final Pattern HOST_PORT_PATTERN = Pattern.compile(
            "^(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,10}|localhost|(([0-9]{1,3}\\.){3})[0-9]{1,3})(:[0-9]{1,5})?$"
    );

    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> filteredServers = new ArrayList<>();
    private ServerAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyHint;
    private ImageView refreshIcon;
    private EditText searchInput;

    private TextView bannerNickText;
    private TextView infoServerName;
    private TextView infoMode;
    private TextView infoMap;
    private TextView infoPlayers;
    private TextView infoPing;

    private String selectedAddressKey = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        bindBanner(view, inflater);
        bindServerList(view, inflater);
        bindInfoAndActions(view);

        reloadAndQueryServers();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNickBadge();
    }

    private void bindBanner(View view, LayoutInflater inflater) {
        bannerNickText = view.findViewById(R.id.banner_nick_text);
        View nickChip = view.findViewById(R.id.banner_nick_chip);
        MangaUiHelper.attachPressFeedback(nickChip);
        nickChip.setOnClickListener(v -> showNickOrConnectDialog(inflater, null, false));
        updateNickBadge();
    }

    private void bindServerList(View view, LayoutInflater inflater) {
        recyclerView = view.findViewById(R.id.home_servers_recycler);
        emptyHint = view.findViewById(R.id.home_servers_empty);
        refreshIcon = view.findViewById(R.id.icon_refresh);
        searchInput = view.findViewById(R.id.home_server_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        adapter = new ServerAdapter(filteredServers);
        adapter.setOnServerClickListener((server, position) -> {
            selectedAddressKey = server.getAddress();
            updateSelectedServerInfo(server);
        });
        adapter.setOnServerLongClickListener((server, position) -> showServerQuickMenu(server));
        recyclerView.setAdapter(adapter);

        View refreshBtn = view.findViewById(R.id.btn_refresh_servers);
        View addServerBtn = view.findViewById(R.id.btn_add_server_inline);
        MangaUiHelper.attachPressFeedback(refreshBtn, addServerBtn);

        refreshBtn.setOnClickListener(v -> {
            spinRefreshIcon();
            reloadAndQueryServers();
        });
        addServerBtn.setOnClickListener(v -> showAddServerDialog(inflater));

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyServerFilter(s != null ? s.toString() : "");
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
    }

    private void bindInfoAndActions(View view) {
        infoServerName = view.findViewById(R.id.info_server_name);
        infoMode = view.findViewById(R.id.info_mode);
        infoMap = view.findViewById(R.id.info_map);
        infoPlayers = view.findViewById(R.id.info_players);
        infoPing = view.findViewById(R.id.info_ping);

        View playBtn = view.findViewById(R.id.btn_play_main);
        MangaUiHelper.attachPressFeedback(playBtn);
        playBtn.setOnClickListener(v -> handlePlayAction());
    }

    private void applyServerFilter(String rawQuery) {
        if (!isAdded()) return;
        String q = rawQuery != null ? rawQuery.trim().toLowerCase(Locale.ROOT) : "";

        ServerInfo prev = adapter.getSelectedServer();
        if (prev != null) {
            selectedAddressKey = prev.getAddress();
        }

        filteredServers.clear();
        for (ServerInfo item : allServers) {
            if (q.isEmpty()
                    || item.name.toLowerCase(Locale.ROOT).contains(q)
                    || item.getAddress().toLowerCase(Locale.ROOT).contains(q)
                    || item.mode.toLowerCase(Locale.ROOT).contains(q)) {
                filteredServers.add(item);
            }
        }

        int targetPos = 0;
        for (int i = 0; i < filteredServers.size(); i++) {
            if (filteredServers.get(i).getAddress().equalsIgnoreCase(selectedAddressKey)) {
                targetPos = i;
                break;
            }
        }
        adapter.notifyDataSetChanged();
        adapter.setSelectedPosition(filteredServers.isEmpty() ? -1 : targetPos);

        if (filteredServers.isEmpty()) {
            emptyHint.setVisibility(View.VISIBLE);
            emptyHint.setText(allServers.isEmpty()
                    ? "Belum ada server. Ketuk + IP untuk menambahkan server SA-MP."
                    : "Server tidak ditemukan untuk \"" + q + "\".");
        } else {
            emptyHint.setVisibility(View.GONE);
        }
        updateSelectedServerInfo(adapter.getSelectedServer());
    }

    private void reloadAndQueryServers() {
        if (!isAdded()) return;
        allServers.clear();
        allServers.addAll(ServerManager.loadServers(requireContext()));
        applyServerFilter(searchInput != null && searchInput.getText() != null
                ? searchInput.getText().toString()
                : "");

        if (!allServers.isEmpty() && getActivity() != null) {
            ServerManager.queryAllServers(getActivity(), allServers, updated -> {
                if (!isAdded()) return;
                int idx = filteredServers.indexOf(updated);
                if (idx >= 0) {
                    adapter.notifyItemChanged(idx);
                    if (idx == adapter.getSelectedPosition()) {
                        updateSelectedServerInfo(updated);
                    }
                }
            });
        }
    }

    private void updateSelectedServerInfo(@Nullable ServerInfo server) {
        if (!isAdded()) return;
        if (server == null) {
            infoServerName.setText("Belum ada server");
            infoMode.setText(": -");
            infoMap.setText(": San Andreas");
            infoPlayers.setText(": 0 / 0");
            infoPing.setText(": -- ms");
            infoPing.setTextColor(ContextCompat.getColor(requireContext(), R.color.ryzn_text_primary));
            return;
        }
        infoServerName.setText(server.name);
        infoMode.setText(": " + server.mode);
        infoMap.setText(": " + (TextUtils.isEmpty(server.map) ? "San Andreas" : server.map));
        infoPlayers.setText(": " + server.getPlayersFormatted());
        infoPing.setText(": " + (server.querying ? "Memeriksa..." : server.getPingFormatted()));
        if (server.online) {
            infoPing.setTextColor(ContextCompat.getColor(requireContext(), R.color.ryzn_status_online));
        } else {
            infoPing.setTextColor(ContextCompat.getColor(requireContext(), R.color.ryzn_text_secondary));
        }
    }

    private void updateNickBadge() {
        if (!isAdded() || bannerNickText == null) return;
        String nick = ServerManager.readSavedNickname(requireContext());
        bannerNickText.setText(TextUtils.isEmpty(nick) ? "Set Nick_Name" : nick);
    }

    private void handlePlayAction() {
        if (!isAdded() || getActivity() == null) return;
        ServerInfo selected = adapter.getSelectedServer();
        if (selected == null) {
            Toast.makeText(getActivity(), "Tambahkan server terlebih dahulu.", Toast.LENGTH_SHORT).show();
            showAddServerDialog(getLayoutInflater());
            return;
        }
        if (!RyznGameDownloader.hasStoragePermission(getActivity())) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestStorageOrAllFilesPermission();
            } else {
                RyznGameDownloader.requestAllFilesPermissionIfNeeded(getActivity());
            }
            return;
        }

        String nick = ServerManager.readSavedNickname(requireContext());
        if (TextUtils.isEmpty(nick) || selected.hasPassword) {
            showNickOrConnectDialog(getLayoutInflater(), selected, true);
            return;
        }
        Toast.makeText(getActivity(), "Menghubungkan sebagai " + nick + "...", Toast.LENGTH_SHORT).show();
        ServerManager.connectToServer(getActivity(), selected, nick, "", null);
    }

    private void showNickOrConnectDialog(LayoutInflater inflater, @Nullable ServerInfo targetServer, boolean connectOnConfirm) {
        if (!isAdded() || getActivity() == null) return;
        View dialogView = inflater.inflate(R.layout.dialog_nick_prompt, null);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView title = dialogView.findViewById(R.id.prompt_title);
        TextView subtitle = dialogView.findViewById(R.id.prompt_subtitle);
        EditText nickInput = dialogView.findViewById(R.id.prompt_nickname_input);
        EditText passInput = dialogView.findViewById(R.id.prompt_password_input);
        Button cancelBtn = dialogView.findViewById(R.id.prompt_cancel_btn);
        Button confirmBtn = dialogView.findViewById(R.id.prompt_confirm_btn);
        MangaUiHelper.attachPressFeedback(cancelBtn, confirmBtn);
        MangaUiHelper.animateEntry(dialogView);

        nickInput.setText(ServerManager.readSavedNickname(requireContext()));
        if (connectOnConfirm && targetServer != null) {
            title.setText(targetServer.name);
            subtitle.setText("Server: " + targetServer.getAddress());
            confirmBtn.setText("Hubungkan");
            if (targetServer.hasPassword) {
                passInput.setVisibility(View.VISIBLE);
                passInput.setText(RyznGameDownloader.getSavedServerPassword());
            }
        } else {
            title.setText("Atur Nick_Name");
            subtitle.setText("Digunakan saat masuk ke server SA-MP");
            confirmBtn.setText("Simpan");
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        confirmBtn.setOnClickListener(v -> {
            String nick = nickInput.getText().toString().trim();
            if (nick.isEmpty()) {
                Toast.makeText(getActivity(), "Masukkan Nick_Name terlebih dahulu.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nick.length() > 24 || nick.contains(" ")) {
                Toast.makeText(getActivity(), "Nick_Name maksimal 24 karakter tanpa spasi.", Toast.LENGTH_SHORT).show();
                return;
            }
            ServerManager.saveNickname(requireContext(), nick);
            updateNickBadge();
            if (connectOnConfirm && targetServer != null) {
                if (!RyznGameDownloader.hasStoragePermission(getActivity())) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).requestStorageOrAllFilesPermission();
                    } else {
                        RyznGameDownloader.requestAllFilesPermissionIfNeeded(getActivity());
                    }
                    return;
                }
                confirmBtn.setEnabled(false);
                String pass = targetServer.hasPassword ? passInput.getText().toString() : "";
                ServerManager.connectToServer(getActivity(), targetServer, nick, pass, dialog::dismiss);
            } else {
                dialog.dismiss();
                Toast.makeText(getActivity(), "Nick_Name disimpan: " + nick, Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showAddServerDialog(LayoutInflater inflater) {
        if (!isAdded()) return;
        View v = inflater.inflate(R.layout.layout_server_add, null);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setView(v).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText addressInput = v.findViewById(R.id.address_input_text);
        Button addBtn = v.findViewById(R.id.add_server);
        MangaUiHelper.attachPressFeedback(addBtn);
        MangaUiHelper.animateEntry(v);

        addressInput.setOnEditorActionListener((tv, id, event) ->
                id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_NEXT);

        addBtn.setOnClickListener(btn -> {
            String raw = addressInput.getText().toString().replace("\n", "").trim();
            if (!HOST_PORT_PATTERN.matcher(raw).matches()) {
                Toast.makeText(getActivity(), "Format host tidak valid. Gunakan HOST:PORT atau IP", Toast.LENGTH_LONG).show();
                return;
            }
            String formatted = raw.contains(":") ? raw : (raw + ":7777");
            if (ServerManager.addServer(requireContext(), formatted)) {
                selectedAddressKey = formatted;
                dialog.dismiss();
                reloadAndQueryServers();
                Toast.makeText(getActivity(), "Server berhasil ditambahkan.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), "Server sudah ada dalam daftar.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showServerQuickMenu(ServerInfo server) {
        if (!isAdded() || server == null) return;
        String[] options = {"Hubungkan Sekarang", "Hapus dari Daftar"};
        new AlertDialog.Builder(requireActivity())
                .setTitle(server.name)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        int idx = filteredServers.indexOf(server);
                        if (idx >= 0) adapter.setSelectedPosition(idx);
                        updateSelectedServerInfo(server);
                        handlePlayAction();
                    } else if (which == 1) {
                        ServerManager.removeServer(requireContext(), server.host, server.port);
                        Toast.makeText(getActivity(), "Server dihapus.", Toast.LENGTH_SHORT).show();
                        reloadAndQueryServers();
                    }
                })
                .show();
    }

    private void spinRefreshIcon() {
        if (refreshIcon == null) return;
        RotateAnimation anim = new RotateAnimation(0f, 360f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(450);
        refreshIcon.startAnimation(anim);
    }
}
