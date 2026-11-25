#include "Settings.h"
#include "Client.h"

#include <rapidjson/document.h>
#include <rapidjson/filereadstream.h>

bool Settings::m_initialized = false;

// server
char Settings::m_host[64] = "127.0.0.1";
int Settings::m_port = 7777;
char Settings::m_pass[64] = "";

// settings
char Settings::m_nick[30] = "samp_player";
int Settings::m_sampversion = 0;
bool Settings::m_newinterface = false;
bool Settings::m_systemkeyboard = false;
bool Settings::m_timestamp = false;
bool Settings::m_fastconnect = false;
bool Settings::m_voice = false;
bool Settings::m_displayfps = false;
bool Settings::m_radarrect = false;
bool Settings::m_modifieddata = false;
bool Settings::m_headmove = true;
bool Settings::m_pcchatbox = true;
int Settings::m_fpslimit = 60;
int Settings::m_chatstrings = 5;
float Settings::m_fontsize = 26.0f;
ImVec2 Settings::m_chatpos = ImVec2(100.0f, 10.0f);
ImVec2 Settings::m_chatsize = ImVec2(400.0f, 150.0f);

void Settings::initialize()
{
	spdlog::info("Initializing settings...");

	if (m_initialized) {
		return;
	}

	if (Client::gameDir() == nullptr) {
		spdlog::error("Failed to initialize settings. (game storage null)");
		return;
	}

	char settings_json_path[0xFF];
	sprintf(settings_json_path, "%sSAMP/settings.json", Client::gameDir());

	FILE* settings_json_file = fopen(settings_json_path, "rb");
	if (settings_json_file == nullptr) {
		const char* internal_path = "/data/data/com.ryzn.samp/files/SAMP/settings.json";
		settings_json_file = fopen(internal_path, "rb");
		if (settings_json_file != nullptr) {
			strcpy(settings_json_path, internal_path);
			spdlog::info("Found settings.json in internal storage: {}", internal_path);
		}
	}

	if (settings_json_file == nullptr) {
		spdlog::warn("settings.json not found, attempting to create default settings.json at {}", settings_json_path);
		settings_json_file = fopen(settings_json_path, "wb");
		if (settings_json_file == nullptr) {
			const char* internal_path = "/data/data/com.ryzn.samp/files/SAMP/settings.json";
			settings_json_file = fopen(internal_path, "wb");
			if (settings_json_file != nullptr) {
				strcpy(settings_json_path, internal_path);
			}
		}
		if (settings_json_file != nullptr) {
			const char* default_json = "{\"client\":{\"server\":{\"host\":\"127.0.0.1\",\"port\":7777,\"password\":\"\"},\"settings\":{\"nick_name\":\"Player\",\"samp_version\":0,\"game_version\":0,\"new_interface\":false,\"system_keyboard\":false,\"timestamp\":false,\"fullscreen\":false,\"fast_connect\":false,\"voice_chat\":true,\"display_fps\":false,\"radar_rect\":false,\"fps_limit\":60,\"chat_strings\":8,\"font_size\":30.0,\"chat_posx\":100,\"chat_posy\":10,\"chat_sizex\":400,\"chat_sizey\":150}}}";
			fwrite(default_json, 1, strlen(default_json), settings_json_file);
			fclose(settings_json_file);
			settings_json_file = fopen(settings_json_path, "rb");
		}
	}

	if (settings_json_file == nullptr) {
		spdlog::warn("Unable to create/open settings.json, proceeding with default settings");
		m_initialized = true;
		return;
	}

	char buffer[0xFFFF];
	rapidjson::FileReadStream fileReadStream(settings_json_file, buffer, sizeof(buffer));

	rapidjson::Document settings_json;
	settings_json.ParseStream<0, rapidjson::UTF8<>, rapidjson::FileReadStream>(fileReadStream);
	fclose(settings_json_file);

	if (settings_json.HasParseError() || !settings_json.IsObject() || !settings_json.HasMember("client")) {
		spdlog::error("Failed to parse settings.json. (parse error or invalid structure), using defaults");
		m_initialized = true;
		return;
	}

	// server
	if (settings_json["client"].HasMember("server")) {
		const auto& server = settings_json["client"]["server"];
		if (server.HasMember("host") && server["host"].IsString()) {
			strcpy(m_host, server["host"].GetString());
		}
		if (server.HasMember("port") && server["port"].IsInt()) {
			m_port = server["port"].GetInt();
		}
		if (server.HasMember("password") && server["password"].IsString()) {
			strcpy(m_pass, server["password"].GetString());
		}
	}

	// settings
	if (settings_json["client"].HasMember("settings")) {
		const auto& s = settings_json["client"]["settings"];
		if (s.HasMember("nick_name") && s["nick_name"].IsString()) strcpy(m_nick, s["nick_name"].GetString());
		if (s.HasMember("samp_version") && s["samp_version"].IsInt()) m_sampversion = s["samp_version"].GetInt();
		if (s.HasMember("system_keyboard") && s["system_keyboard"].IsBool()) m_systemkeyboard = s["system_keyboard"].GetBool();
		if (s.HasMember("timestamp") && s["timestamp"].IsBool()) m_timestamp = s["timestamp"].GetBool();
		if (s.HasMember("fast_connect") && s["fast_connect"].IsBool()) m_fastconnect = s["fast_connect"].GetBool();
		if (s.HasMember("voice_chat") && s["voice_chat"].IsBool()) m_voice = s["voice_chat"].GetBool();
		if (s.HasMember("display_fps") && s["display_fps"].IsBool()) m_displayfps = s["display_fps"].GetBool();
		if (s.HasMember("radar_rect") && s["radar_rect"].IsBool()) m_radarrect = s["radar_rect"].GetBool();
		if (s.HasMember("modified_data") && s["modified_data"].IsBool()) m_modifieddata = s["modified_data"].GetBool();
		if (s.HasMember("pc_chatbox") && s["pc_chatbox"].IsBool()) m_pcchatbox = s["pc_chatbox"].GetBool();
		if (s.HasMember("fps_limit") && s["fps_limit"].IsInt()) m_fpslimit = s["fps_limit"].GetInt();
		if (s.HasMember("chat_strings") && s["chat_strings"].IsInt()) m_chatstrings = s["chat_strings"].GetInt();
		if (s.HasMember("font_size") && s["font_size"].IsNumber()) m_fontsize = s["font_size"].GetFloat();
		if (s.HasMember("chat_posx") && s["chat_posx"].IsInt()) m_chatpos.x = s["chat_posx"].GetInt();
		if (s.HasMember("chat_posy") && s["chat_posy"].IsInt()) m_chatpos.y = s["chat_posy"].GetInt();
		if (s.HasMember("chat_sizex") && s["chat_sizex"].IsInt()) m_chatsize.x = s["chat_sizex"].GetInt();
		if (s.HasMember("chat_sizey") && s["chat_sizey"].IsInt()) m_chatsize.y = s["chat_sizey"].GetInt();
	}

	spdlog::info("host {}", m_host);
	spdlog::info("port {}", m_port);
	spdlog::info("password {}", m_pass);

	spdlog::info("nick_name {}", m_nick);
	spdlog::info("samp_version {}", m_sampversion);
	spdlog::info("new_interface {}", m_newinterface);
	spdlog::info("system_keyboard {}", m_systemkeyboard);
	spdlog::info("timestamp {}", m_timestamp);
	spdlog::info("fast_connect {}", m_fastconnect);
	spdlog::info("voice_chat {}", m_voice);
	spdlog::info("display_fps {}", m_displayfps);
	spdlog::info("radar_rect {}", m_radarrect);
	spdlog::info("modified_data {}", m_modifieddata);
	spdlog::info("pc_chatbox {}", m_pcchatbox);
	spdlog::info("fps_limit {}", m_fpslimit);
	spdlog::info("chat_strings {}", m_chatstrings);
	spdlog::info("font_size {}", m_fontsize);
	spdlog::info("chat_pos {} {}", m_chatpos.x, m_chatpos.y);
	spdlog::info("chat_size {} {}", m_chatsize.x, m_chatsize.y);

	m_initialized = true;
}
