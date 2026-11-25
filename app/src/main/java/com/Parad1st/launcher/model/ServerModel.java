package com.byparad1st.launcher.model;

public class ServerModel {
    public String server_name;
    public String server_host;
    public String server_port;
    public String server_password;
    public String server_gamemode;
    public String server_players;
    public String server_maxplayers;
    public String server_language;
    public boolean online;

    public ServerModel(String server_name, String server_host, String server_port, String server_password, String server_gamemode, String server_players, String server_maxplayers, String server_language) {
        this(server_name, server_host, server_port, server_password, server_gamemode, server_players, server_maxplayers, server_language, false);
    }

    public ServerModel(String server_name, String server_host, String server_port, String server_password, String server_gamemode, String server_players, String server_maxplayers, String server_language, boolean online) {
        this.server_name = server_name;
        this.server_host = server_host;
        this.server_port = server_port;
        this.server_password = server_password;
        this.server_gamemode = server_gamemode;
        this.server_players = server_players;
        this.server_maxplayers = server_maxplayers;
        this.server_language = server_language;
        this.online = online;
    }
}
