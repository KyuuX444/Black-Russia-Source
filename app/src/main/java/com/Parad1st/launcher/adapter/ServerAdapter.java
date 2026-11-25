package com.byparad1st.launcher.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.byparad1st.game.R;
import com.byparad1st.game.core.GTASA;
import com.byparad1st.launcher.Registration.reg;
import com.byparad1st.launcher.activity.LoaderActivity;
import com.byparad1st.launcher.model.ServerModel;
import com.byparad1st.launcher.other.AlyaLiteDownloader;
import com.byparad1st.launcher.other.SampQuery;
import com.byparad1st.launcher.other.ServerStorage;

import org.ini4j.Wini;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {

    private final Activity activity;
    public List<ServerModel> serverModel;
    private final Runnable onListMutated;
    private final ExecutorService queryExecutor = Executors.newCachedThreadPool();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ConstraintLayout serverItem;
        final TextView serverName;
        final TextView serverAddress;
        final ImageView serverPassword;
        final TextView serverGamemode;
        final TextView serverPlayers;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            serverItem = itemView.findViewById(R.id.server_item);
            serverName = itemView.findViewById(R.id.server_name);
            serverAddress = itemView.findViewById(R.id.server_address);
            serverPassword = itemView.findViewById(R.id.server_password);
            serverGamemode = itemView.findViewById(R.id.server_gamemode);
            serverPlayers = itemView.findViewById(R.id.server_players);
        }
    }

    public ServerAdapter(Activity activity, List<ServerModel> serverModel, Runnable onListMutated) {
        this.activity = activity;
        this.serverModel = serverModel;
        this.onListMutated = onListMutated;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_server_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= serverModel.size()) {
            return;
        }
        final ServerModel model = serverModel.get(position);
        holder.serverName.setText(model.server_name);
        holder.serverAddress.setText("Alamat: " + model.server_host + ":" + model.server_port);
        holder.serverPassword.setImageResource("1".equals(model.server_password)
                ? R.drawable.ic_locked
                : R.drawable.ic_unlocked);
        holder.serverGamemode.setText("Gamemode: " + model.server_gamemode);
        holder.serverPlayers.setText("Pemain: " + model.server_players + " / " + model.server_maxplayers);

        holder.serverItem.setOnClickListener(v -> showServerInfoDialog(model));
    }

    private void showServerInfoDialog(ServerModel model) {
        View view = activity.getLayoutInflater().inflate(R.layout.layout_server_info, null);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView serverName = view.findViewById(R.id.server_name);
        TextView serverAddress = view.findViewById(R.id.server_address);
        TextView serverPlayers = view.findViewById(R.id.server_players);
        TextView serverGamemode = view.findViewById(R.id.server_gamemode);
        TextView serverLanguage = view.findViewById(R.id.server_language);

        EditText nicknameInput = view.findViewById(R.id.nickname_input_text);
        EditText passwordInput = view.findViewById(R.id.password_input_text);
        Button deleteButton = view.findViewById(R.id.delete_button);
        Button connectButton = view.findViewById(R.id.connect_button);

        nicknameInput.setOnEditorActionListener((tv, actionId, event) ->
                actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT);
        passwordInput.setOnEditorActionListener((tv, actionId, event) ->
                actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT);

        String savedNick = readNickname();
        String savedPass = AlyaLiteDownloader.getSavedServerPassword();
        nicknameInput.setText(savedNick);
        passwordInput.setText(savedPass);

        serverName.setText(model.server_name);
        serverAddress.setText("Alamat: " + model.server_host + ":" + model.server_port);
        serverPlayers.setText("Pemain: " + model.server_players + " / " + model.server_maxplayers);
        serverGamemode.setText("Gamemode: " + model.server_gamemode);
        serverLanguage.setText("Bahasa: " + model.server_language);

        boolean hasPassword = "1".equals(model.server_password);
        passwordInput.setVisibility(hasPassword ? View.VISIBLE : View.GONE);

        deleteButton.setOnClickListener(btn -> {
            ServerStorage.deleteServerFromFavorites(activity, model.server_host, model.server_port);
            Toast.makeText(activity, "Server dihapus dari daftar favorit.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onListMutated != null) {
                onListMutated.run();
            }
        });

        connectButton.setOnClickListener(btn -> {
            String nick = nicknameInput.getText().toString().trim();
            if (nick.isEmpty()) {
                Toast.makeText(activity, "Masukkan Nick_Name terlebih dahulu.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (nick.length() > 24 || nick.contains(" ")) {
                Toast.makeText(activity, "Nick_Name maksimal 24 karakter tanpa spasi.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AlyaLiteDownloader.hasStoragePermission(activity)) {
                AlyaLiteDownloader.requestAllFilesPermissionIfNeeded(activity);
                return;
            }

            String pass = hasPassword ? passwordInput.getText().toString() : "";
            activity.getSharedPreferences("samp_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("nick_name", nick)
                    .apply();
            reg.setNick(nick);

            AlyaLiteDownloader.ensureClientConfigAndFonts(nick);
            AlyaLiteDownloader.saveServerConfig(model.server_host + ":" + model.server_port, pass);

            dialog.dismiss();

            if (AlyaLiteDownloader.isGameInstalled()) {
                activity.startActivity(new Intent(activity, GTASA.class));
            } else {
                activity.startActivity(new Intent(activity, LoaderActivity.class));
            }
        });

        dialog.show();
    }

    private String readNickname() {
        try {
            File ini = AlyaLiteDownloader.getSettingsFile();
            if (ini.exists()) {
                Wini w = new Wini(ini);
                String name = w.get("client", "name");
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String pref = activity.getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getString("nick_name", "");
        if (!pref.isEmpty()) return pref;
        return reg.getNick();
    }

    public void queryServers(List<ServerModel> fullList) {
        for (ServerModel target : fullList) {
            final String host = target.server_host;
            final String port = target.server_port;
            queryExecutor.execute(() -> {
                boolean fetched = false;
                for (int attempt = 0; attempt < 2 && !fetched; attempt++) {
                    SampQuery query = null;
                    try {
                        query = new SampQuery(host, Integer.parseInt(port));
                        if (query.isOnline()) {
                            String[] infos = query.getInfo();
                            if (infos != null && infos[3] != null && !infos[3].trim().isEmpty()) {
                                target.server_password = infos[0];
                                target.server_players = infos[1];
                                target.server_maxplayers = infos[2];
                                target.server_name = infos[3];
                                target.server_gamemode = infos[4] != null && !infos[4].trim().isEmpty() ? infos[4] : "SA-MP";
                                target.server_language = infos[5] != null && !infos[5].trim().isEmpty() ? infos[5] : "-";
                                target.online = true;
                                fetched = true;
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (query != null) {
                            query.close();
                        }
                    }
                    if (!fetched) {
                        try {
                            Thread.sleep(400);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }
                if (!fetched) {
                    target.server_name = host + ":" + port + " (Ketuk untuk Hubungkan)";
                    target.server_gamemode = "SA-MP Server";
                    target.online = false;
                }
                if (!activity.isFinishing()) {
                    activity.runOnUiThread(() -> {
                        int idx = serverModel.indexOf(target);
                        if (idx >= 0) {
                            notifyItemChanged(idx);
                        }
                    });
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return serverModel.size();
    }
}
