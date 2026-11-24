package com.byparad1st.launcher.other;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;

import org.ini4j.Wini;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class AlyaLiteDownloader {

    public static final String GAME_DIR_NAME = "ryznSAMP";
    public static final String CLIENT_NAME = "ryzn SA-MP";
    public static final int DEFAULT_SERVER_PORT = 7777;
    public static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/KyuuX444/alya-assets/refs/heads/main/alyaupdate.json";
    public static final String DEFAULT_LITE_LIST_URL = "https://raw.githubusercontent.com/KyuuX444/alya-assets/refs/heads/main/lite_list.json";
    public static final String DEFAULT_SAMP_LIST_URL = "https://raw.githubusercontent.com/KyuuX444/alya-assets/refs/heads/main/samp_list.json";

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private static class AssetItem {
        final String path;
        final String url;
        final long size;

        AssetItem(String path, String url, long size) {
            this.path = path;
            this.url = url;
            this.size = size;
        }
    }

    private volatile boolean cancelled = false;
    private Thread workerThread;

    public static File getGameDir() {
        return new File(Environment.getExternalStorageDirectory(), GAME_DIR_NAME);
    }

    public static File getSettingsFile() {
        return new File(getGameDir(), "SAMP/settings.ini");
    }

    public static boolean isGameInstalled() {
        File dir = getGameDir();
        return new File(dir, "texdb/gta3.img").exists() && getSettingsFile().exists();
    }

    public static boolean hasStoragePermission(Context context) {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestAllFilesPermissionIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(
                    activity,
                    "Izinkan akses semua file agar ryzn SA-MP dapat menyimpan data game di /storage/emulated/0/" + GAME_DIR_NAME + "/",
                    Toast.LENGTH_LONG
            ).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void ensureClientConfigAndFonts(String nickname) {
        File gameDir = getGameDir();
        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        File srcFontsDir = new File(gameDir, "fonts");
        File dstFontsDir = new File(gameDir, "SAMP/fonts");
        if (!dstFontsDir.exists()) {
            dstFontsDir.mkdirs();
        }

        if (srcFontsDir.isDirectory()) {
            File[] fontFiles = srcFontsDir.listFiles();
            if (fontFiles != null) {
                for (File fontFile : fontFiles) {
                    if (fontFile.isFile()) {
                        File dst = new File(dstFontsDir, fontFile.getName());
                        if (!dst.exists() || dst.length() != fontFile.length()) {
                            copyFile(fontFile, dst);
                        }
                    }
                }
            }
            File arialLower = new File(srcFontsDir, "arial.ttf");
            File arialUpper = new File(dstFontsDir, "Arial.ttf");
            if (arialLower.exists() && (!arialUpper.exists() || arialUpper.length() != arialLower.length())) {
                copyFile(arialLower, arialUpper);
            }
        }

        File settingsFile = getSettingsFile();
        if (settingsFile.getParentFile() != null && !settingsFile.getParentFile().exists()) {
            settingsFile.getParentFile().mkdirs();
        }
        String cleanNick = (nickname == null || nickname.trim().isEmpty()) ? "Player" : nickname.trim();
        if (!settingsFile.exists()) {
            String iniContent = "[client]\n"
                    + "name = " + cleanNick + "\n"
                    + "password = \n"
                    + "pagesize = 10\n\n"
                    + "[server]\n"
                    + "host = \n"
                    + "port = 7777\n\n"
                    + "[debug]\n"
                    + "debug = 0\n"
                    + "online = 0\n\n"
                    + "[gui]\n"
                    + "Font = Arial.ttf\n"
                    + "FontSize = 30.0\n"
                    + "FontOutline = 2\n"
                    + "ChatPosX = 325.0\n"
                    + "ChatPosY = 25.0\n"
                    + "ChatSizeX = 1150.0\n"
                    + "ChatSizeY = 220.0\n"
                    + "ChatMaxMessages = 8\n"
                    + "HealthBarWidth = 60.0\n"
                    + "HealthBarHeight = 10.0\n"
                    + "ScoreBoardSizeX = 1024.0\n"
                    + "ScoreBoardSizeY = 768.0\n"
                    + "androidKeyboard = 0\n"
                    + "hud = 0\n"
                    + "Cutout = 0\n"
                    + "fps = 60\n";
            try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                fos.write(iniContent.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        } else if (nickname != null && !nickname.trim().isEmpty()) {
            try {
                Wini w = new Wini(settingsFile);
                w.put("client", "name", cleanNick);
                w.store();
            } catch (Exception ignored) {
            }
        }
    }

    public static String[] parseServerAddress(String rawInput) {
        if (rawInput == null) return new String[]{"", String.valueOf(DEFAULT_SERVER_PORT)};
        String s = rawInput.trim();
        if (s.isEmpty()) return new String[]{"", String.valueOf(DEFAULT_SERVER_PORT)};

        int port = DEFAULT_SERVER_PORT;
        int colonIdx = s.lastIndexOf(':');
        if (colonIdx > 0 && colonIdx < s.length() - 1) {
            String hostPart = s.substring(0, colonIdx).trim();
            String portPart = s.substring(colonIdx + 1).trim();
            try {
                int parsed = Integer.parseInt(portPart);
                if (parsed > 0 && parsed <= 65535) {
                    port = parsed;
                    s = hostPart;
                }
            } catch (NumberFormatException ignored) {
            }
        } else if (colonIdx == s.length() - 1) {
            s = s.substring(0, colonIdx).trim();
        }
        return new String[]{s, String.valueOf(port)};
    }

    public static String formatServerAddress(String host, int port) {
        if (host == null || host.trim().isEmpty()) return "";
        int validPort = (port > 0 && port <= 65535) ? port : DEFAULT_SERVER_PORT;
        return host.trim() + ":" + validPort;
    }

    public static String getSavedServerAddress() {
        try {
            File f = getSettingsFile();
            if (!f.exists()) return "";
            Wini w = new Wini(f);
            String host = w.get("server", "host");
            String portStr = w.get("server", "port");
            if (host == null || host.trim().isEmpty()) return "";
            int port = DEFAULT_SERVER_PORT;
            if (portStr != null) {
                try {
                    int parsed = Integer.parseInt(portStr.trim());
                    if (parsed > 0 && parsed <= 65535) port = parsed;
                } catch (NumberFormatException ignored) {
                }
            }
            return formatServerAddress(host, port);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String getSavedServerPassword() {
        try {
            File f = getSettingsFile();
            if (!f.exists()) return "";
            Wini w = new Wini(f);
            String pass = w.get("client", "password");
            return pass != null ? pass.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean saveServerConfig(String rawAddress, String password) {
        try {
            ensureClientConfigAndFonts("");
            File f = getSettingsFile();
            Wini w = new Wini(f);
            String[] parsed = parseServerAddress(rawAddress);
            w.put("server", "host", parsed[0]);
            w.put("server", "port", parsed[1]);
            if (password != null) {
                w.put("client", "password", password);
            }
            w.store();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String detectGpuVendor() {
        EGL10 egl = null;
        EGLDisplay display = EGL10.EGL_NO_DISPLAY;
        EGLSurface surface = EGL10.EGL_NO_SURFACE;
        EGLContext context = EGL10.EGL_NO_CONTEXT;
        try {
            egl = (EGL10) EGLContext.getEGL();
            display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (display == EGL10.EGL_NO_DISPLAY) {
                return "etc";
            }
            int[] version = new int[2];
            if (!egl.eglInitialize(display, version)) {
                return "etc";
            }
            int[] configSpec = {
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RENDERABLE_TYPE, 4,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfig = new int[1];
            if (!egl.eglChooseConfig(display, configSpec, configs, 1, numConfig) || numConfig[0] <= 0) {
                return "etc";
            }
            int[] contextAttribs = {0x3098, 2, EGL10.EGL_NONE};
            context = egl.eglCreateContext(display, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs);
            int[] surfaceAttribs = {EGL10.EGL_WIDTH, 64, EGL10.EGL_HEIGHT, 64, EGL10.EGL_NONE};
            surface = egl.eglCreatePbufferSurface(display, configs[0], surfaceAttribs);
            if (surface == EGL10.EGL_NO_SURFACE || context == EGL10.EGL_NO_CONTEXT) {
                return "etc";
            }
            if (!egl.eglMakeCurrent(display, surface, surface, context)) {
                return "etc";
            }
            GL10 gl = (GL10) context.getGL();
            String renderer = gl.glGetString(GL10.GL_RENDERER);
            String extensions = gl.glGetString(GL10.GL_EXTENSIONS);
            String combined = ((renderer != null ? renderer : "") + " " + (extensions != null ? extensions : "")).toLowerCase(Locale.US);
            if (combined.contains("adreno") || combined.contains("gl_ext_texture_compression_s3tc") || combined.contains("dxt")) {
                return "dxt";
            }
            if (combined.contains("powervr") || combined.contains("rogue") || combined.contains("imgtec")) {
                return "pvr";
            }
            return "etc";
        } catch (Throwable ignored) {
            return "etc";
        } finally {
            if (egl != null && display != EGL10.EGL_NO_DISPLAY) {
                try {
                    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                    if (surface != EGL10.EGL_NO_SURFACE) egl.eglDestroySurface(display, surface);
                    if (context != EGL10.EGL_NO_CONTEXT) egl.eglDestroyContext(display, context);
                    egl.eglTerminate(display);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void cancel() {
        cancelled = true;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public void startDownload(
            final Activity activity,
            final RoundCornerProgressBar progressBar,
            final TextView percentText,
            final TextView statusText,
            final Callback callback
    ) {
        cancelled = false;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        workerThread = new Thread(() -> {
            try {
                updateUi(mainHandler, statusText, percentText, progressBar,
                        "Mendeteksi GPU & membaca daftar aset...", "0%", 0);

                final String gpu = detectGpuVendor();
                String liteUrl = DEFAULT_LITE_LIST_URL;
                String sampUrl = DEFAULT_SAMP_LIST_URL;

                try {
                    JSONObject updateJson = new JSONObject(fetchText(UPDATE_JSON_URL));
                    String uLite = updateJson.optString("lite_list_url", "");
                    String uSamp = updateJson.optString("samp_list_url", "");
                    if (!uLite.isEmpty()) liteUrl = uLite;
                    if (!uSamp.isEmpty()) sampUrl = uSamp;
                } catch (Exception ignored) {
                }

                if (cancelled) return;

                updateUi(mainHandler, statusText, percentText, progressBar,
                        "Mengambil daftar data ryzn SA-MP (GPU: " + gpu.toUpperCase(Locale.US) + ")...", "0%", 0);

                JSONObject liteJson = new JSONObject(fetchText(liteUrl));
                JSONObject sampJson = new JSONObject(fetchText(sampUrl));

                Map<String, AssetItem> itemsMap = new LinkedHashMap<>();
                collectMatchingItems(liteJson.optJSONArray("files"), gpu, false, itemsMap);
                collectMatchingItems(sampJson.optJSONArray("files"), gpu, true, itemsMap);

                List<AssetItem> allItems = new ArrayList<>(itemsMap.values());
                long sumBytes = 0L;
                for (AssetItem item : allItems) {
                    if (item.size > 0) sumBytes += item.size;
                }
                final long totalBytes = sumBytes;

                File gameDir = getGameDir();
                if (!gameDir.exists() && !gameDir.mkdirs()) {
                    throw new IOException("Gagal membuat folder penyimpanan: " + gameDir.getAbsolutePath());
                }

                long downloadedBytes = 0L;
                final int totalFiles = allItems.size();
                long lastUiUpdate = 0L;

                for (int i = 0; i < totalFiles; i++) {
                    if (cancelled) return;
                    final int fileIndex = i;
                    AssetItem item = allItems.get(i);
                    File targetFile = safeChildFile(gameDir, item.path);
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    final String shortName = targetFile.getName();
                    if (targetFile.exists() && item.size > 0 && targetFile.length() == item.size) {
                        downloadedBytes += item.size;
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate > 150 || i == totalFiles - 1) {
                            lastUiUpdate = now;
                            int pct = totalBytes > 0 ? (int) Math.min(100L, (downloadedBytes * 100L) / totalBytes)
                                    : ((i + 1) * 100) / Math.max(1, totalFiles);
                            updateUi(mainHandler, statusText, percentText, progressBar,
                                    String.format(Locale.US, "Memeriksa file (%s) %d/%d: %s", gpu.toUpperCase(Locale.US), i + 1, totalFiles, shortName),
                                    formatProgress(downloadedBytes, totalBytes, pct),
                                    pct);
                        }
                        continue;
                    }

                    final long bytesBeforeFile = downloadedBytes;
                    long receivedForFile = downloadWithRetry(item.url, targetFile, bytesRead -> {
                        if (cancelled) throw new IOException("Unduhan dibatalkan");
                        long currentTotal = bytesBeforeFile + bytesRead;
                        int pct = totalBytes > 0 ? (int) Math.min(100L, (currentTotal * 100L) / totalBytes)
                                : ((fileIndex * 100) / Math.max(1, totalFiles));
                        String status = String.format(Locale.US, "Mengunduh data ryzn SA-MP (%s) %d/%d: %s",
                                gpu.toUpperCase(Locale.US), fileIndex + 1, totalFiles, shortName);
                        String pctStr = formatProgress(currentTotal, totalBytes, pct);
                        updateUi(mainHandler, statusText, percentText, progressBar, status, pctStr, pct);
                    });
                    downloadedBytes += (item.size > 0 ? item.size : receivedForFile);
                }

                if (cancelled) return;

                updateUi(mainHandler, statusText, percentText, progressBar,
                        "Menyiapkan konfigurasi & font ryzn SA-MP...", "100%", 100);
                ensureClientConfigAndFonts(com.byparad1st.launcher.Registration.reg.getNick());

                if (!cancelled && callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (final Throwable t) {
                if (!cancelled && callback != null) {
                    String msg = t.getMessage() != null ? t.getMessage() : "Kesalahan jaringan saat mengunduh data game";
                    mainHandler.post(() -> callback.onError(msg));
                }
            }
        }, "AlyaLiteDownloader");
        workerThread.start();
    }

    private static void collectMatchingItems(
            JSONArray filesArray,
            String gpu,
            boolean isSampList,
            Map<String, AssetItem> out
    ) {
        if (filesArray == null) return;
        for (int i = 0; i < filesArray.length(); i++) {
            JSONObject obj = filesArray.optJSONObject(i);
            if (obj == null) continue;
            String itemGpu = obj.optString("gpu", "all").trim().toLowerCase(Locale.US);
            String relPath = obj.optString("path", "").trim();
            String url = obj.optString("url", "").trim();
            long size = obj.optLong("size", 0L);

            if (relPath.isEmpty() || url.isEmpty()) continue;
            if (!"all".equals(itemGpu) && !itemGpu.equals(gpu)) continue;

            if (isSampList) {
                String lowerPath = relPath.toLowerCase(Locale.US);
                if (lowerPath.startsWith("texdb/samp/samp.")) {
                    boolean matchesGpu = lowerPath.startsWith("texdb/samp/samp." + gpu + ".");
                    boolean isTxt = lowerPath.equals("texdb/samp/samp.txt");
                    boolean isUnc = lowerPath.startsWith("texdb/samp/samp.unc.");
                    if (!matchesGpu && !isTxt && !isUnc) {
                        continue;
                    }
                }
            }

            out.put(relPath, new AssetItem(relPath, url, size));
        }
    }

    private interface ByteProgressListener {
        void onProgress(long totalBytesRead) throws IOException;
    }

    private long downloadWithRetry(String urlStr, File targetFile, ByteProgressListener listener) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (cancelled) throw new IOException("Dibatalkan");
            File tmpFile = new File(targetFile.getParentFile(), targetFile.getName() + ".part");
            try {
                long downloaded = downloadSingleFile(urlStr, tmpFile, listener);
                if (targetFile.exists() && !targetFile.delete()) {
                    copyFile(tmpFile, targetFile);
                    tmpFile.delete();
                } else if (!tmpFile.renameTo(targetFile)) {
                    copyFile(tmpFile, targetFile);
                    tmpFile.delete();
                }
                return downloaded;
            } catch (IOException e) {
                lastError = e;
                tmpFile.delete();
                if (attempt < 3 && !cancelled) {
                    try {
                        Thread.sleep(600L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Unduhan terhenti", ie);
                    }
                }
            }
        }
        throw lastError != null ? lastError : new IOException("Gagal mengunduh: " + targetFile.getName());
    }

    private long downloadSingleFile(String urlStr, File tmpFile, ByteProgressListener listener) throws IOException {
        HttpURLConnection conn = openConnectionFollowingRedirects(urlStr);
        try (InputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
             OutputStream out = new FileOutputStream(tmpFile)) {
            byte[] buffer = new byte[32768];
            long totalRead = 0L;
            long lastNotify = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
                long now = System.currentTimeMillis();
                if (listener != null && now - lastNotify >= 150) {
                    lastNotify = now;
                    listener.onProgress(totalRead);
                }
            }
            out.flush();
            if (listener != null) {
                listener.onProgress(totalRead);
            }
            return totalRead;
        } finally {
            conn.disconnect();
        }
    }

    private static String fetchText(String urlStr) throws IOException {
        HttpURLConnection conn = openConnectionFollowingRedirects(urlStr);
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnectionFollowingRedirects(String urlStr) throws IOException {
        String currentUrl = urlStr;
        for (int hops = 0; hops < 6; hops++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "ryzn-SA-MP-Android");
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect kosong dari server: HTTP " + code);
                }
                currentUrl = new URL(new URL(currentUrl), location).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " saat mengakses " + currentUrl);
            }
            return conn;
        }
        throw new IOException("Terlalu banyak redirect: " + urlStr);
    }

    private static File safeChildFile(File root, String relPath) throws IOException {
        String clean = relPath.replace("\\", "/");
        if (clean.startsWith("/") || clean.contains("..")) {
            throw new IOException("Path aset tidak aman: " + relPath);
        }
        File target = new File(root, clean);
        String rootCanonical = root.getCanonicalPath();
        String targetCanonical = target.getCanonicalPath();
        if (!targetCanonical.equals(rootCanonical) && !targetCanonical.startsWith(rootCanonical + File.separator)) {
            throw new IOException("Path aset keluar dari direktori tujuan: " + relPath);
        }
        return target;
    }

    private static void copyFile(File src, File dst) {
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            dst.getParentFile().mkdirs();
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } catch (IOException ignored) {
        }
    }

    private static void updateUi(
            Handler mainHandler,
            TextView statusText,
            TextView percentText,
            RoundCornerProgressBar progressBar,
            String status,
            String pctText,
            int pct
    ) {
        mainHandler.post(() -> {
            if (statusText != null) statusText.setText(status);
            if (percentText != null) percentText.setText(pctStrSafe(pctText));
            if (progressBar != null) {
                progressBar.setMax(100);
                progressBar.setProgress(pct);
            }
        });
    }

    private static String pctStrSafe(String s) {
        return s != null ? s : "";
    }

    private static String formatProgress(long current, long total, int pct) {
        if (total > 0) {
            return String.format(Locale.US, "%s / %s (%d%%)",
                    Utils.bytesIntoHumanReadable(current),
                    Utils.bytesIntoHumanReadable(total),
                    pct);
        }
        return Utils.bytesIntoHumanReadable(current);
    }
}
