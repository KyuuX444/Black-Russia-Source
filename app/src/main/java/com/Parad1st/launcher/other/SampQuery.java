package com.byparad1st.launcher.other;

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

public class SampQuery {

    private DatagramSocket socket = null;
    private InetAddress serverip = null;
    private String serveraddress = "";
    private final int serverport;
    private boolean isValidAddr = true;
    private final String charset = "windows-1251";
    private final Random random = new Random();

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
            socket.setSoTimeout(1800);
        } catch (SocketException e) {
            this.isValidAddr = false;
            serveraddress = null;
        }
    }

    public boolean isOnline() {
        if (isValidAddr && socket != null && serveraddress != null) {
            byte[] randomBytes = generateRandomBytes();
            byte[] data = null;

            try {
                String pktData = "p" + new String(randomBytes, charset);
                sendPacket(initPacket(pktData));
                data = receiveData();
            } catch (Exception ignored) {
            }

            if (data != null && data.length >= 15
                    && data[10] == 112 && data[11] == randomBytes[0]
                    && data[12] == randomBytes[1] && data[13] == randomBytes[2]) {
                return data[14] == randomBytes[3];
            }
        }
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
            infos[1] = String.valueOf(buff.getShort());
            infos[2] = String.valueOf(buff.getShort());

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
            return getpacket.getData();
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
        if (length < 0 || length > 1024) return "";
        byte[] n = new byte[length];
        try {
            for (int x = 0; x < length; x++) {
                n[x] = buff.get();
            }
        } catch (Exception ignored) {
        }
        return new String(n, charset);
    }
}
