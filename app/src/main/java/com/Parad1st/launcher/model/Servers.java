package com.byparad1st.launcher.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Servers {

	@SerializedName("color")
	@Expose
	private String color;

	@SerializedName("dopname")
	@Expose
	private String dopname;

	@SerializedName("name")
	@Expose
	private String name;

	@SerializedName("online")
	@Expose
	private int online;

	@SerializedName("maxonline")
	@Expose
	private int maxonline;

	@SerializedName("ip")
	@Expose
	private String ip;

	@SerializedName("port")
	@Expose
	private int port;

	public Servers(String color, String dopname, String name, int online, int maxonline) {
		this(color, dopname, name, online, maxonline, "", 7777);
	}

	public Servers(String color, String dopname, String name, int online, int maxonline, String ip, int port) {
		this.color = color;
		this.dopname = dopname;
		this.name = name;
		this.online = online;
		this.maxonline = maxonline;
		this.ip = ip;
		this.port = port;
	}
	 
	public String getname() {
		return name;
	}

    public String getDopname() {
		 return dopname;
	}

    public String getColor() {
		return color;
	}
	
	public int getOnline(){
		return online;
	}

	public int getmaxOnline(){
		return maxonline;
	}

	public String getIp() {
		return ip != null ? ip.trim() : "";
	}

	public int getPort() {
		return (port > 0 && port <= 65535) ? port : 7777;
	}
}