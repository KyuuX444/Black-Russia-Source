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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ryzn.game.R;
import com.ryzn.launcher.adapter.ServerAdapter;
import com.ryzn.launcher.model.ServerInfo;
import com.ryzn.launcher.other.RyznGameDownloader;
import com.ryzn.launcher.other.ServerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Full-page Server manager tab for searching, adding, deleting, querying, and launching servers.
 */
public class FavoriteServersFragment extends Fragment {

    private static final Pattern HOST_PORT_PATTERN = Pattern.compile(
            "^(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,10}|localhost|(([0-9]{1,3}\\.){3})[0-9]{1,3})(:[0-9]{1,5})?$"
    );

    private ServerAdapter serverAdapter;
    private final List<ServerInfo> allServers = new ArrayList<>();
    private final List<ServerInfo> visibleServers = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView emptyHint;
    private EditText searchEditText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_servers, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.servers_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));

        serverAdapter = new ServerAdapter(visibleServers);
        serverAdapter.setOnServerClickListener((server, pos) -> showServerDetailDialog(server));
        recyclerView.setAdapter(serverAdapter);

        emptyHint = view.findViewById(R.id.empty_servers_hint);
        swipeRefreshLayout = view.findViewById(R.id.refresh);
        swipeRefreshLayout.setOnRefreshListener(this::refreshServers);

        searchEditText = view.findViewById(R.id.search_edit_text);
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilter(searchEditText.getText().toString());
                return true;
            }
            return false;
        });
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        Button addServerBtn = view.findViewById(R.id.add_server);
        addServerBtn.setOnClickListener(v -> showAddServerDialog(inflater));

        refreshServers();
        return view;
    }

    private void showServerDetailDialog(ServerInfo server) {
        if (!isAdded() || getActivity() == null || server == null) return;
        View v = getActivity().getLayoutInflater().inflate(R.layout.layout_server_info, null);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setView(v).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView serverName = v.findViewById(R.id.server_name);
        TextView serverAddress = v.findViewById(R.id.server_address);
        TextView serverPlayers = v.findViewById(R.id.server_players);
        TextView serverGamemode = v.findViewById(R.id.server_gamemode);
        TextView serverLanguage = v.findViewById(R.id.server_language);

        EditText nicknameInput = v.findViewById(R.id.nickname_input_text);
        EditText passwordInput = v.findViewById(R.id.password_input_text);
        Button deleteButton = v.findViewById(R.id.delete_button);
        Button connectButton = v.findViewById(R.id.connect_button);

        nicknameInput.setText(ServerManager.readSavedNickname(requireContext()));
        passwordInput.setText(RyznGameDownloader.getSavedServerPassword());

        serverName.setText(server.name);
        serverAddress.setText("Alamat: " + server.getAddress());
        serverPlayers.setText("Pemain: " + server.getPlayersFormatted() + " (" + server.getPingFormatted() + ")");
        serverGamemode.setText("Mode: " + server.mode);
        serverLanguage.setText("Map: " + (TextUtils.isEmpty(server.map) ? "San Andreas" : server.map));

        passwordInput.setVisibility(server.hasPassword ? View.VISIBLE : View.GONE);

        deleteButton.setOnClickListener(btn -> {
            ServerManager.removeServer(requireContext(), server.host, server.port);
            Toast.makeText(getActivity(), "Server dihapus dari daftar favorit.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshServers();
        });

        connectButton.setOnClickListener(btn -> {
            String nick = nicknameInput.getText().toString().trim();
            if (nick.isEmpty()) {
                Toast.makeText(getActivity(), "Masukkan Nick_Name terlebih dahulu.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nick.length() > 24 || nick.contains(" ")) {
                Toast.makeText(getActivity(), "Nick_Name maksimal 24 karakter tanpa spasi.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!RyznGameDownloader.hasStoragePermission(requireActivity())) {
                if (getActivity() instanceof com.ryzn.launcher.activity.MainActivity) {
                    ((com.ryzn.launcher.activity.MainActivity) getActivity()).requestStorageOrAllFilesPermission();
                } else {
                    RyznGameDownloader.requestAllFilesPermissionIfNeeded(requireActivity());
                }
                return;
            }
            btn.setEnabled(false);
            String pass = server.hasPassword ? passwordInput.getText().toString() : "";
            ServerManager.connectToServer(requireActivity(), server, nick, pass, () -> {
                btn.setEnabled(true);
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    private void showAddServerDialog(LayoutInflater inflater) {
        View v = inflater.inflate(R.layout.layout_server_add, null);
        AlertDialog dialog = new AlertDialog.Builder(requireActivity()).setView(v).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText addressInput = v.findViewById(R.id.address_input_text);
        Button addBtn = v.findViewById(R.id.add_server);

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
                Toast.makeText(getActivity(), "Server berhasil ditambahkan ke favorit.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                refreshServers();
            } else {
                Toast.makeText(getActivity(), "Server sudah ada di daftar favorit.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    public void refreshServers() {
        if (!isAdded()) return;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        allServers.clear();
        allServers.addAll(ServerManager.loadServers(requireContext()));
        applyFilter(searchEditText != null ? searchEditText.getText().toString() : "");
        if (!allServers.isEmpty() && getActivity() != null) {
            ServerManager.queryAllServers(getActivity(), allServers, updated -> {
                if (!isAdded()) return;
                int idx = visibleServers.indexOf(updated);
                if (idx >= 0) {
                    serverAdapter.notifyItemChanged(idx);
                }
            });
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void applyFilter(String query) {
        visibleServers.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) {
            visibleServers.addAll(allServers);
        } else {
            for (ServerInfo server : allServers) {
                String addr = server.getAddress().toLowerCase();
                String name = server.name != null ? server.name.toLowerCase() : "";
                if (addr.contains(q) || name.contains(q)) {
                    visibleServers.add(server);
                }
            }
        }
        serverAdapter.notifyDataSetChanged();
        if (emptyHint != null) {
            emptyHint.setVisibility(allServers.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }
}
