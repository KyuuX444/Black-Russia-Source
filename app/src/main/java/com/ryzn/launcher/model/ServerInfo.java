package com.ryzn.launcher.model;

/**
 * Immutable-friendly data model representing an SA-MP server entry and its queried runtime state.
 */
public class ServerInfo {
    public String name;
    public String host;
    public String port;
    public String mode;
    public String map;
    public String language;
    public String players;
    public String maxPlayers;
    public int pingMs;
    public boolean hasPassword;
    public boolean online;
    public boolean querying;

    public ServerInfo(String name, String host, String port) {
        this(name, host, port, "SA-MP", "San Andreas", "-", "0", "0", -1, false, false);
    }

    public ServerInfo(
            String name,
            String host,
            String port,
            String mode,
            String map,
            String language,
            String players,
            String maxPlayers,
            int pingMs,
            boolean hasPassword,
            boolean online
    ) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.mode = mode;
        this.map = map;
        this.language = language;
        this.players = players;
        this.maxPlayers = maxPlayers;
        this.pingMs = pingMs;
        this.hasPassword = hasPassword;
        this.online = online;
        this.querying = false;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    public String getPlayersFormatted() {
        return players + " / " + maxPlayers;
    }

    public String getPingFormatted() {
        if (!online || pingMs < 0) {
            return "-- ms";
        }
        return pingMs + " ms";
    }
}
