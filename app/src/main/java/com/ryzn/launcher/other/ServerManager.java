package com.ryzn.launcher.other;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.ryzn.game.core.GTASA;
import com.ryzn.launcher.Registration.reg;
import com.ryzn.launcher.activity.LoaderActivity;
import com.ryzn.launcher.model.ServerInfo;

import org.ini4j.Wini;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates server persistence, asynchronous UDP status queries, nickname persistence,
 * and connection handoff into GTA:SA / LoaderActivity.
 */
public class ServerManager {

    private static final String SERVERS_FILE = "servers.json";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public interface OnServerUpdatedListener {
        void onServerUpdated(ServerInfo server);
    }

    public interface OnConnectCompleteListener {
        void onComplete();
    }

    public enum ClientState {
        READY,
        NEEDS_PERMISSION,
        NEEDS_DOWNLOAD
    }

    private static File getServersFile(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            base = ctx.getFilesDir();
        }
        return new File(base, SERVERS_FILE);
    }

    public static List<ServerInfo> loadServers(Context ctx) {
        List<ServerInfo> list = new ArrayList<>();
        try {
            File f = getServersFile(ctx);
            if (!f.exists() || f.length() == 0) {
                String saved = RyznGameDownloader.getSavedServerAddress();
                if (!TextUtils.isEmpty(saved)) {
                    addServer(ctx, saved);
                } else {
                    addServer(ctx, "127.0.0.1:7777");
                }
            }
            if (!f.exists()) return list;
            String jsonString = readFile(f);
            if (jsonString.isEmpty()) return list;
            JSONObject root = new JSONObject(jsonString);
            JSONArray arr = root.optJSONArray("servers");
            if (arr == null) return list;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String host = obj.optString("host", "").trim();
                String port = obj.optString("port", "7777").trim();
                if (!host.isEmpty()) {
                    list.add(new ServerInfo(host + ":" + port, host, port));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static boolean addServer(Context ctx, String rawAddress) {
        try {
            String[] parsed = RyznGameDownloader.parseServerAddress(rawAddress);
            String host = parsed[0];
            String port = parsed[1];
            if (host == null || host.isEmpty()) return false;

            File f = getServersFile(ctx);
            JSONObject root;
            if (!f.exists() || f.length() == 0) {
                root = new JSONObject();
                root.put("servers", new JSONArray());
            } else {
                String content = readFile(f);
                root = content.isEmpty() ? new JSONObject() : new JSONObject(content);
                if (!root.has("servers")) {
                    root.put("servers", new JSONArray());
                }
            }

            JSONArray arr = root.getJSONArray("servers");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (host.equalsIgnoreCase(obj.optString("host")) && port.equals(obj.optString("port"))) {
                    return false;
                }
            }

            JSONObject entry = new JSONObject();
            entry.put("host", host);
            entry.put("port", port);
            arr.put(entry);

            writeFile(f, root.toString(2));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean removeServer(Context ctx, String host, String port) {
        try {
            File f = getServersFile(ctx);
            if (!f.exists()) return false;
            String content = readFile(f);
            if (content.isEmpty()) return false;
            JSONObject root = new JSONObject(content);
            JSONArray arr = root.optJSONArray("servers");
            if (arr == null) return false;

            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!host.equalsIgnoreCase(obj.optString("host")) || !port.equals(obj.optString("port"))) {
                    next.put(obj);
                }
            }
            root.put("servers", next);
            writeFile(f, root.toString(2));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void queryAllServers(Activity activity, List<ServerInfo> servers, OnServerUpdatedListener listener) {
        if (activity == null || servers == null) return;
        for (ServerInfo server : servers) {
            server.querying = true;
            EXECUTOR.execute(() -> {
                boolean fetched = false;
                for (int attempt = 0; attempt < 2 && !fetched; attempt++) {
                    SampQuery query = null;
                    try {
                        query = new SampQuery(server.host, Integer.parseInt(server.port));
                        if (query.isOnline()) {
                            int rtt = query.getLastPingMs();
                            String[] infos = query.getInfo();
                            if (infos != null && infos[3] != null && !infos[3].trim().isEmpty()) {
                                String mapName = query.getMapName();
                                server.hasPassword = "1".equals(infos[0]);
                                server.players = infos[1];
                                server.maxPlayers = infos[2];
                                server.name = infos[3].trim();
                                server.mode = (infos[4] != null && !infos[4].trim().isEmpty()) ? infos[4].trim() : "SA-MP";
                                server.language = (infos[5] != null && !infos[5].trim().isEmpty()) ? infos[5].trim() : "-";
                                server.map = !TextUtils.isEmpty(mapName) ? mapName : "San Andreas";
                                server.pingMs = rtt;
                                server.online = true;
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
                            Thread.sleep(250);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (!fetched) {
                    server.online = false;
                    server.pingMs = -1;
                    if (TextUtils.isEmpty(server.name) || server.name.equals(server.getAddress())) {
                        server.name = server.getAddress();
                    }
                    if (TextUtils.isEmpty(server.mode)) {
                        server.mode = "SA-MP Server";
                    }
                    if (TextUtils.isEmpty(server.map)) {
                        server.map = "San Andreas";
                    }
                }
                server.querying = false;
                if (!activity.isFinishing() && listener != null) {
                    activity.runOnUiThread(() -> listener.onServerUpdated(server));
                }
            });
        }
    }

    public static void connectToServer(
            Activity activity,
            ServerInfo server,
            String nickname,
            String password,
            OnConnectCompleteListener listener
    ) {
        if (activity == null || server == null) return;
        String cleanNick = nickname != null ? nickname.trim() : "";
        saveNickname(activity, cleanNick);

        String rawHost = server.host != null ? server.host.trim() : "127.0.0.1";
        String targetPort = (server.port != null && !server.port.trim().isEmpty()) ? server.port.trim() : "7777";
        String pass = server.hasPassword && password != null ? password : "";

        EXECUTOR.execute(() -> {
            String connectHost = rawHost;
            if (!rawHost.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
                try {
                    InetAddress[] addrs = InetAddress.getAllByName(rawHost);
                    for (InetAddress addr : addrs) {
                        if (addr instanceof Inet4Address) {
                            connectHost = addr.getHostAddress();
                            break;
                        }
                    }
                    if (connectHost.equals(rawHost) && addrs.length > 0) {
                        connectHost = addrs[0].getHostAddress();
                    }
                } catch (Throwable ignored) {
                    connectHost = rawHost;
                }
            }
            final String resolvedAddress = connectHost + ":" + targetPort;
            activity.runOnUiThread(() -> {
                if (listener != null) {
                    listener.onComplete();
                }
                RyznGameDownloader.saveServerConfig(resolvedAddress, pass);
                if (RyznGameDownloader.isGameInstalled()) {
                    activity.startActivity(new Intent(activity, GTASA.class));
                } else {
                    activity.startActivity(new Intent(activity, LoaderActivity.class));
                }
            });
        });
    }

    public static String readSavedNickname(Context context) {
        if (context == null) return "";
        try {
            File ini = RyznGameDownloader.getSettingsFile();
            if (ini.exists()) {
                Wini w = new Wini(ini);
                String name = w.get("client", "name");
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String pref = context.getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getString("nick_name", "");
        if (!pref.isEmpty()) return pref;
        return reg.getNick();
    }

    public static void saveNickname(Context context, String nickname) {
        if (context == null || nickname == null || nickname.trim().isEmpty()) return;
        String cleanNick = nickname.trim();
        context.getSharedPreferences("samp_settings", Context.MODE_PRIVATE)
                .edit()
                .putString("nick_name", cleanNick)
                .apply();
        reg.setNick(cleanNick);
        if (RyznGameDownloader.hasStoragePermission(context)) {
            RyznGameDownloader.ensureClientConfigAndFonts(context, cleanNick);
        }
    }

    public static ClientState getClientState(Context context) {
        if (context == null || !RyznGameDownloader.hasStoragePermission(context)) {
            return ClientState.NEEDS_PERMISSION;
        }
        if (RyznGameDownloader.isGameInstalled()) {
            return ClientState.READY;
        }
        return ClientState.NEEDS_DOWNLOAD;
    }

    private static String readFile(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
        }
        return bos.toString("UTF-8").trim();
    }

    private static void writeFile(File f, String content) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }
}
