package com.ryzn.launcher.other;

import android.content.Context;

import com.ryzn.launcher.model.ServerModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ServerStorage {

    private static final String FAVORITES_FILE = "servers.json";

    private static File getFavoritesFile(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            base = ctx.getFilesDir();
        }
        return new File(base, FAVORITES_FILE);
    }

    public static List<ServerModel> loadFavorites(Context ctx) {
        List<ServerModel> list = new ArrayList<>();
        try {
            File f = getFavoritesFile(ctx);
            if (!f.exists() || f.length() == 0) {
                // Auto-seed from existing settings.ini server if already configured
                String saved = RyznGameDownloader.getSavedServerAddress();
                if (saved != null && !saved.trim().isEmpty()) {
                    addServerToFavorites(ctx, saved);
                } else {
                    return list;
                }
            }
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
                    list.add(new ServerModel("Memeriksa status...", host, port, "0", "SA-MP 0.3.7", "0", "0", "Indonesia", false));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static boolean addServerToFavorites(Context ctx, String address) {
        try {
            String[] parsed = RyznGameDownloader.parseServerAddress(address);
            String host = parsed[0];
            String port = parsed[1];
            if (host == null || host.isEmpty()) return false;

            File f = getFavoritesFile(ctx);
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

    public static boolean deleteServerFromFavorites(Context ctx, String host, String port) {
        try {
            File f = getFavoritesFile(ctx);
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
