package com.ryzn.launcher.other;

import android.os.SystemClock;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * Lightweight SA-MP UDP query client supporting ping RTT measurement, server info ('i'),
 * and mapname rule lookup ('r').
 */
public class SampQuery {

    private DatagramSocket socket = null;
    private InetAddress serverip = null;
    private String serveraddress = "";
    private final int serverport;
    private boolean isValidAddr = true;
    private final String charset = "windows-1251";
    private final Random random = new Random();
    private int lastPingMs = -1;

    public SampQuery(String srvip, int srvport) {
        this.serverport = srvport;
        try {
            serverip = InetAddress.getByName(srvip);
            serveraddress = serverip.getHostAddress();
        } catch (UnknownHostException e) {
            this.isValidAddr = false;
            serveraddress = null;
        }

        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(1400);
        } catch (SocketException e) {
            this.isValidAddr = false;
            serveraddress = null;
        }
    }

    public int getLastPingMs() {
        return lastPingMs;
    }

    public boolean isOnline() {
        if (isValidAddr && socket != null && serveraddress != null) {
            byte[] randomBytes = generateRandomBytes();
            byte[] data = null;

            long t0 = SystemClock.elapsedRealtime();
            try {
                String pktData = "p" + new String(randomBytes, charset);
                sendPacket(initPacket(pktData));
                data = receiveData();
            } catch (Exception ignored) {
            }

            if (data != null && data.length >= 15
                    && data[10] == 112 && data[11] == randomBytes[0]
                    && data[12] == randomBytes[1] && data[13] == randomBytes[2]
                    && data[14] == randomBytes[3]) {
                lastPingMs = (int) Math.max(1L, SystemClock.elapsedRealtime() - t0);
                return true;
            }
        }
        lastPingMs = -1;
        return false;
    }

    private byte[] generateRandomBytes() {
        byte[] randomBytes = new byte[4];
        random.nextBytes(randomBytes);
        randomBytes[0] = (byte) ((Math.abs(randomBytes[0]) % 100) + 110);
        randomBytes[1] = (byte) (Math.abs(randomBytes[1]) % 127);
        randomBytes[2] = (byte) (Math.abs(randomBytes[2]) % 127);
        randomBytes[3] = (byte) (Math.abs(randomBytes[3]) % 50);
        return randomBytes;
    }

    public String[] getInfo() {
        String[] infos = new String[6];
        try {
            sendPacket(initPacket("i"));
            byte[] raw = receiveData();
            if (raw == null) return null;
            ByteBuffer buff = ByteBuffer.wrap(raw);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.position(11);

            infos[0] = String.valueOf(buff.get() == 1 ? 1 : 0);
            infos[1] = String.valueOf(buff.getShort() & 0xFFFF);
            infos[2] = String.valueOf(buff.getShort() & 0xFFFF);

            int hostnamelength = buff.getInt();
            infos[3] = convert(buff, hostnamelength);

            int gamemodelength = buff.getInt();
            infos[4] = convert(buff, gamemodelength);

            int languagelength = buff.getInt();
            infos[5] = convert(buff, languagelength);

            return infos;
        } catch (Exception e) {
            return null;
        }
    }

    public String getMapName() {
        try {
            sendPacket(initPacket("r"));
            byte[] raw = receiveData();
            if (raw == null || raw.length < 13) return null;
            ByteBuffer buff = ByteBuffer.wrap(raw);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.position(11);
            int ruleCount = buff.getShort() & 0xFFFF;
            for (int i = 0; i < ruleCount && buff.remaining() >= 2; i++) {
                int keyLen = buff.get() & 0xFF;
                String key = convert(buff, keyLen);
                if (buff.remaining() < 1) break;
                int valLen = buff.get() & 0xFF;
                String val = convert(buff, valLen);
                if ("mapname".equalsIgnoreCase(key.trim()) && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private DatagramPacket initPacket(String type) {
        try {
            if (serveraddress == null) return null;
            String pktdata = "SAMP" + "zalupa" + type;
            byte[] ips = pktdata.getBytes(charset);

            StringTokenizer ip = new StringTokenizer(serveraddress, ".");
            ips[4] = (byte) Integer.parseInt(ip.nextToken());
            ips[5] = (byte) Integer.parseInt(ip.nextToken());
            ips[6] = (byte) Integer.parseInt(ip.nextToken());
            ips[7] = (byte) Integer.parseInt(ip.nextToken());
            ips[8] = (byte) (serverport & 255);
            ips[9] = (byte) (serverport >> 8 & 255);
            return new DatagramPacket(ips, ips.length, serverip, serverport);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] receiveData() {
        if (socket == null) {
            return null;
        }
        byte[] data = new byte[3072];
        DatagramPacket getpacket = new DatagramPacket(data, 3072);
        try {
            socket.receive(getpacket);
            byte[] trimmed = new byte[getpacket.getLength()];
            System.arraycopy(getpacket.getData(), 0, trimmed, 0, getpacket.getLength());
            return trimmed;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void sendPacket(DatagramPacket d) {
        try {
            if (socket != null && d != null) {
                socket.send(d);
            }
        } catch (IOException ignored) {
        }
    }

    public void close() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private String convert(ByteBuffer buff, int length) throws UnsupportedEncodingException {
        if (length < 0 || length > 1024 || buff.remaining() < length) {
            length = Math.max(0, Math.min(buff.remaining(), length));
        }
        byte[] n = new byte[length];
        buff.get(n);
        return new String(n, charset);
    }
}
