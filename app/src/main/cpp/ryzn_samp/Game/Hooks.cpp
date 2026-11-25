#include <sys/sysinfo.h>
#include <sys/stat.h>
#include <EGL/egl.h>
#include <algorithm>
#include <cctype>
#include <cmath>
#include <dirent.h>
#include <mutex>
#include <sstream>
#include <unordered_map>

#include "Game.h"
#include "../UI/UI.h"
#include "../Net/NetGame.h"
#include "../Client.h"
#include "../Settings.h"
#include "ThreadOptimizer.h"
#include "../Java/Java.h"
#include "Pickups.h"
#include "../DefaultDataFiles.h"

extern UI *pUI;
extern Game *pGame;
extern NetGame *pNetGame;

DECL_HOOK(void, AND_TouchEvent, int type, int num, int posX, int posY)
{
	if (Game::IsGamePaused())
	{
		return AND_TouchEvent(type, num, posX, posY);
	}

	if (pUI != nullptr)
	{
		switch (type)
		{
		case 2: // push
			pUI->touchEvent(ImVec2(posX, posY), TouchType::push);
			break;

		case 3: // move
			pUI->touchEvent(ImVec2(posX, posY), TouchType::move);
			break;

		case 1: // pop
			pUI->touchEvent(ImVec2(posX, posY), TouchType::pop);
			break;
		}

		if ((pUI->keyboard() && pUI->keyboard()->visible()) || (pUI->dialog() && pUI->dialog()->visible()))
		{
			AND_TouchEvent(1, 0, 0, 0);
			return;
		}
		else
		{
			if (pNetGame && pNetGame->GetTextDrawPool())
			{
				if (!pNetGame->GetTextDrawPool()->onTouchEvent(type, posX, posY))
				{
					return AND_TouchEvent(1, 0, 0, 0);
				}
			}
		}
	}

	if (pGame->IsGameInputEnabled())
	{
		AND_TouchEvent(type, num, posX, posY);
	}
	else
	{
		AND_TouchEvent(1, 0, 0, 0);
	}
}

DECL_HOOK(void, DisplayScreen)
{
	// Render GTA:SA's default loading screen without custom overlay UI
	DisplayScreen();
}

static bool check_file_readable(const std::string& path)
{
	FILE* f = fopen(path.c_str(), "rb");
	if (f) {
		fclose(f);
		return true;
	}
	return false;
}

DECL_HOOK(int, OS_FileGetPosition, void* handle)
{
	if (!handle) return 0;
	return OS_FileGetPosition(handle);
}

DECL_HOOK(int, OS_FileRead, void* handle, void* buffer, int size)
{
	if (!handle) return 0;
	return OS_FileRead(handle, buffer, size);
}

DECL_HOOK(int, OS_FileSetPosition, void* handle, int pos)
{
	if (!handle) return 0;
	return OS_FileSetPosition(handle, pos);
}

DECL_HOOK(int, OS_FileSize, void* handle)
{
	if (!handle) return 0;
	return OS_FileSize(handle);
}

const uintptr_t FAKE_HANDLE_TIMECYC = (uintptr_t)0xDEADC001;
const uintptr_t FAKE_HANDLE_COLORCYCLE = (uintptr_t)0xDEADC002;
const uintptr_t FAKE_HANDLE_POPCYCLE = (uintptr_t)0xDEADC004;

static size_t s_timecyc_offset = 0;
static size_t s_colorcycle_offset = 0;
static char s_timecyc_line_buf[1024];
static char s_colorcycle_line_buf[1024];

static bool str_contains_case_insensitive(const char* haystack, const char* needle)
{
	if (!haystack || !needle) return false;
	std::string h = haystack;
	std::string n = needle;
	std::transform(h.begin(), h.end(), h.begin(), ::tolower);
	std::transform(n.begin(), n.end(), n.begin(), ::tolower);
	return h.find(n) != std::string::npos;
}

static char* get_next_line_from_buf(const unsigned char* data, size_t data_len, size_t& offset, char* out_buf, size_t out_max)
{
	while (offset < data_len) {
		size_t line_start = offset;
		while (offset < data_len && data[offset] != '\n' && data[offset] != '\r') {
			offset++;
		}
		size_t line_len = offset - line_start;
		while (offset < data_len && (data[offset] == '\n' || data[offset] == '\r')) {
			offset++;
		}
		if (line_len == 0) continue;
		if (line_len >= out_max) line_len = out_max - 1;
		memcpy(out_buf, data + line_start, line_len);
		out_buf[line_len] = '\0';

		char* p = out_buf;
		while (*p == ' ' || *p == '\t') p++;
		if (*p == '/' && *(p + 1) == '/') continue;
		if (*p == '\0') continue;

		return p;
	}
	return nullptr;
}

DECL_HOOK(char*, CFileLoader_LoadLine, uintptr_t handle)
{
	if (handle == FAKE_HANDLE_TIMECYC) {
		char* line = get_next_line_from_buf(default_timecyc_dat, default_timecyc_dat_len, s_timecyc_offset, s_timecyc_line_buf, sizeof(s_timecyc_line_buf));
		if (!line) {
			static char dummy_tc[] = "0 0 0  0 0 0  0 0 0  0 0 0  0 0 0 0 0 0 0 0 0 0 0 0.0 0.0 0 0 0 0 0 0 0.0 0 0 0 0 0 0 0 0 0 0\n";
			return dummy_tc;
		}
		return line;
	}
	if (handle == FAKE_HANDLE_COLORCYCLE) {
		char* line = get_next_line_from_buf(default_colorcycle_dat, default_colorcycle_dat_len, s_colorcycle_offset, s_colorcycle_line_buf, sizeof(s_colorcycle_line_buf));
		if (!line) {
			static char dummy_cc[] = "0.0 0.0 0 0 0.0 0.0 0 0 0.0 0.0 0 0\n";
			return dummy_cc;
		}
		return line;
	}
	if (handle == FAKE_HANDLE_POPCYCLE) {
		static char dummy_popcycle[] = "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n";
		return dummy_popcycle;
	}
	if (!handle) {
		return nullptr;
	}
	return CFileLoader_LoadLine(handle);
}

DECL_HOOK(bool, CFileMgr_ReadLine, uintptr_t handle, char *buffer, int maxSize)
{
	if (handle == FAKE_HANDLE_TIMECYC) {
		char* line = get_next_line_from_buf(default_timecyc_dat, default_timecyc_dat_len, s_timecyc_offset, s_timecyc_line_buf, sizeof(s_timecyc_line_buf));
		if (line && buffer && maxSize > 0) {
			strncpy(buffer, line, maxSize - 1);
			buffer[maxSize - 1] = '\0';
			return true;
		}
		return false;
	}
	if (handle == FAKE_HANDLE_COLORCYCLE) {
		char* line = get_next_line_from_buf(default_colorcycle_dat, default_colorcycle_dat_len, s_colorcycle_offset, s_colorcycle_line_buf, sizeof(s_colorcycle_line_buf));
		if (line && buffer && maxSize > 0) {
			strncpy(buffer, line, maxSize - 1);
			buffer[maxSize - 1] = '\0';
			return true;
		}
		return false;
	}
	if (handle == FAKE_HANDLE_POPCYCLE) {
		static char dummy_popcycle[] = "0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n";
		if (buffer && maxSize > 0) {
			strncpy(buffer, dummy_popcycle, maxSize - 1);
			buffer[maxSize - 1] = '\0';
			return true;
		}
		return false;
	}
	if (!handle) return false;
	return CFileMgr_ReadLine(handle, buffer, maxSize);
}

static bool file_readable(const std::string& path) {
	struct stat st{};
	if (stat(path.c_str(), &st) != 0 || S_ISDIR(st.st_mode)) {
		return false;
	}
	return access(path.c_str(), R_OK) == 0;
}

static std::mutex s_fsCacheMutex;
static std::vector<std::string> s_modBases;
static std::vector<std::string> s_stockBases;
static std::vector<std::string> s_validBases;
static bool s_basesInitialized = false;
static std::unordered_map<std::string, std::unordered_map<std::string, std::string>> s_dirCache;
static std::unordered_map<std::string, std::string> s_pathCache;
static std::vector<std::string> s_customTdbNames;

static std::string to_lower_ascii(std::string s)
{
	for (char& c : s) {
		c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
	}
	return s;
}

static void push_unique_dir(std::vector<std::string>& out, const std::string& rawDir)
{
	if (rawDir.empty()) return;
	std::string dir = rawDir;
	if (dir.back() != '/') dir += '/';
	for (const auto& existing : out) {
		if (existing == dir) return;
	}
	struct stat st{};
	if (stat(dir.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
		out.push_back(std::move(dir));
	}
}

static void discover_modpack_roots(const std::string& parentDir, std::vector<std::string>& out)
{
	static const char* kAssetFolders[] = {
		"anim", "audio", "cleo", "data", "models", "movies",
		"mp3", "samp", "scripts", "texdb", "text", "tracks"
	};

	std::string modsDir;
	DIR* parent = opendir(parentDir.c_str());
	if (!parent) return;
	struct dirent* pe;
	while ((pe = readdir(parent)) != nullptr) {
		if (strcasecmp(pe->d_name, "mods") == 0) {
			modsDir = parentDir + pe->d_name + "/";
			break;
		}
	}
	closedir(parent);
	if (modsDir.empty()) return;

	// Direct files under <base>/mods/ take highest priority.
	push_unique_dir(out, modsDir);

	// Also allow modpack bundles such as <base>/mods/<PackName>/data/... or texdb/...
	std::vector<std::string> packDirs;
	DIR* mdir = opendir(modsDir.c_str());
	if (mdir) {
		struct dirent* me;
		while ((me = readdir(mdir)) != nullptr) {
			if (me->d_name[0] == '.') continue;
			std::string lower = to_lower_ascii(me->d_name);
			bool isStandardAssetSubdir = false;
			for (const char* stdName : kAssetFolders) {
				if (lower == stdName) {
					isStandardAssetSubdir = true;
					break;
				}
			}
			if (isStandardAssetSubdir) continue;
			std::string full = modsDir + me->d_name + "/";
			struct stat st{};
			if (stat(full.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
				packDirs.push_back(std::move(full));
			}
		}
		closedir(mdir);
	}
	std::sort(packDirs.begin(), packDirs.end());
	for (const auto& pd : packDirs) {
		push_unique_dir(out, pd);
	}
}

static void init_file_bases_locked()
{
	if (s_basesInitialized) {
		return;
	}
	const bool modDataMode = Settings::modifieddata();
	const char* candidateBases[] = {
		Client::gameDir(),
		"/storage/emulated/0/ryznSAMP/",
		"/storage/emulated/0/Android/data/com.ryzn.samp/files/",
		modDataMode ? nullptr : "/data/data/com.ryzn.samp/files/",
		"/storage/emulated/0/Android/data/com.rockstargames.gtasa/files/",
		modDataMode ? "/data/data/com.ryzn.samp/files/" : nullptr
	};
	for (const char* b : candidateBases) {
		if (!b || !b[0]) continue;
		std::string norm(b);
		if (norm.back() != '/') norm += '/';
		push_unique_dir(s_stockBases, norm);
	}
	// Modpack directories have priority over all stock bases (skip internal shadow dir when modified_data is active).
	for (const auto& stock : s_stockBases) {
		if (modDataMode && stock == "/data/data/com.ryzn.samp/files/") continue;
		discover_modpack_roots(stock, s_modBases);
	}
	for (const auto& modBase : s_modBases) {
		push_unique_dir(s_validBases, modBase);
	}
	for (const auto& stock : s_stockBases) {
		push_unique_dir(s_validBases, stock);
	}
	s_basesInitialized = true;
}

static std::vector<std::string> get_valid_bases()
{
	std::lock_guard<std::mutex> lock(s_fsCacheMutex);
	init_file_bases_locked();
	return s_validBases;
}

static std::string lookup_cached_entry(const std::string& dirPathWithSlash, const std::string& segment)
{
	std::string lowerSeg = to_lower_ascii(segment);
	auto it = s_dirCache.find(dirPathWithSlash);
	if (it == s_dirCache.end()) {
		std::unordered_map<std::string, std::string> entries;
		DIR* dir = opendir(dirPathWithSlash.c_str());
		if (dir) {
			struct dirent* entry;
			while ((entry = readdir(dir)) != nullptr) {
				if (entry->d_name[0] == '.' &&
					(entry->d_name[1] == '\0' || (entry->d_name[1] == '.' && entry->d_name[2] == '\0'))) {
					continue;
				}
				entries.emplace(to_lower_ascii(entry->d_name), entry->d_name);
			}
			closedir(dir);
		}
		it = s_dirCache.emplace(dirPathWithSlash, std::move(entries)).first;
	}

	auto found = it->second.find(lowerSeg);
	return found != it->second.end() ? found->second : "";
}

static std::string resolve_case_insensitive_path(const std::string& baseDir, const std::string& relPath)
{
	std::string cleanRel = relPath;
	for (char& c : cleanRel) {
		if (c == '\\') c = '/';
	}
	while (!cleanRel.empty() && cleanRel.front() == '/') {
		cleanRel.erase(cleanRel.begin());
	}
	if (cleanRel.empty()) return "";

	std::string currentPath = baseDir;
	if (!currentPath.empty() && currentPath.back() != '/') {
		currentPath += '/';
	}

	std::string exact = currentPath + cleanRel;
	if (file_readable(exact)) {
		return exact;
	}

	std::stringstream ss(cleanRel);
	std::string segment;
	while (std::getline(ss, segment, '/')) {
		if (segment.empty() || segment == ".") continue;

		std::string actualName = lookup_cached_entry(currentPath, segment);
		if (actualName.empty()) {
			return "";
		}
		currentPath += actualName;
		currentPath += '/';
	}

	if (!currentPath.empty() && currentPath.back() == '/') {
		currentPath.pop_back();
	}
	if (file_readable(currentPath)) {
		return currentPath;
	}
	return "";
}

static std::string extract_relative_asset_path(const std::string& requestedPath)
{
	std::string clean = requestedPath;
	for (char& c : clean) {
		if (c == '\\') c = '/';
	}

	std::string rel = clean;
	const char* pFiles = strstr(clean.c_str(), "/files/");
	if (pFiles) {
		rel = pFiles + 7;
	} else if (!clean.empty() && clean.front() == '/') {
		const char* gd = Client::gameDir();
		if (gd && gd[0] && clean.find(gd) == 0) {
			rel = clean.substr(strlen(gd));
		}
	}
	while (!rel.empty() && rel.front() == '/') {
		rel.erase(rel.begin());
	}
	return rel;
}

static std::string resolve_in_base_list(const std::vector<std::string>& bases, const std::string& rel)
{
	if (rel.empty()) return "";
	for (const auto& base : bases) {
		std::string resolved = resolve_case_insensitive_path(base, rel);
		if (!resolved.empty()) {
			return resolved;
		}

		// Special case: decision files (PedEvent.txt, *.ped, *.grp) might be inside data/Decision/
		if (rel.find("Decision") == std::string::npos && (rel.find(".ped") != std::string::npos || rel.find(".grp") != std::string::npos || rel == "PedEvent.txt")) {
			std::string tryDec1 = resolve_case_insensitive_path(base, "data/Decision/" + rel);
			if (!tryDec1.empty()) {
				return tryDec1;
			}
			std::string tryDec2 = resolve_case_insensitive_path(base, "data/Decision/Allowed/" + rel);
			if (!tryDec2.empty()) {
				return tryDec2;
			}
		}

		// Bare config/data filenames requested without directory prefix (e.g. TouchDefaultPhoneWidescreen.cfg, ShopUVs.dat)
		if (rel.find('/') == std::string::npos) {
			std::string tryData = resolve_case_insensitive_path(base, "data/" + rel);
			if (!tryData.empty()) {
				return tryData;
			}
		}
	}
	return "";
}

static std::string find_real_file(const std::string& requestedPath, bool useCache = true)
{
	std::string rel = extract_relative_asset_path(requestedPath);
	if (rel.empty()) {
		return "";
	}

	std::string cacheKey = to_lower_ascii(rel);
	std::lock_guard<std::mutex> lock(s_fsCacheMutex);
	init_file_bases_locked();

	if (useCache) {
		auto cached = s_pathCache.find(cacheKey);
		if (cached != s_pathCache.end()) {
			return cached->second;
		}
	}

	std::string foundPath = resolve_in_base_list(s_validBases, rel);

	if (useCache) {
		s_pathCache.emplace(std::move(cacheKey), foundPath);
	}
	return foundPath;
}

static std::string find_modpack_only_file(const std::string& relPath)
{
	std::string rel = extract_relative_asset_path(relPath);
	if (rel.empty()) {
		return "";
	}
	std::lock_guard<std::mutex> lock(s_fsCacheMutex);
	init_file_bases_locked();
	return resolve_in_base_list(s_modBases, rel);
}

static std::string to_storage_relative(const std::string& absPath)
{
	const char* rootBuf = g_saSym ? g_saSym->GetSymbol<const char*>("StorageRootBuffer") : nullptr;
	const char* bases[] = {
		rootBuf,
		Client::gameDir()
	};
	for (const char* b : bases) {
		if (!b || !b[0]) continue;
		std::string base(b);
		while (!base.empty() && base.back() == '/') {
			base.pop_back();
		}
		if (!base.empty() && absPath.size() > base.size() + 1 &&
			absPath.compare(0, base.size(), base) == 0 &&
			absPath[base.size()] == '/') {
			return absPath.substr(base.size() + 1);
		}
	}
	return "";
}

DECL_HOOK(uintptr_t, CFileMgr_OpenFile, const char* filename, const char* mode)
{
	static thread_local bool s_retrying = false;
	uintptr_t handle = CFileMgr_OpenFile(filename, mode);
	if (!handle && filename) {
		bool isReadOnly = (!mode || (mode[0] == 'r' && strchr(mode, '+') == nullptr));
		if (!s_retrying && isReadOnly && filename[0] != '/') {
			std::string real = find_real_file(filename, true);
			if (!real.empty()) {
				std::string rel = to_storage_relative(real);
				const std::string& retryPath = !rel.empty() ? rel : real;
				if (retryPath != filename) {
					s_retrying = true;
					handle = CFileMgr_OpenFile(retryPath.c_str(), mode);
					s_retrying = false;
					if (handle) {
						spdlog::debug("CFileMgr::OpenFile(\"{}\") resolved -> \"{}\"", filename, retryPath);
						return handle;
					}
				}
			}
		}
		spdlog::debug("CFileMgr::OpenFile(\"{}\", \"{}\") returned NULL", filename, mode ? mode : "null");
		if (str_contains_case_insensitive(filename, "TIMECYC")) {
			s_timecyc_offset = 0;
			spdlog::info("Providing in-memory fallback handle for {}", filename);
			return FAKE_HANDLE_TIMECYC;
		}
		if (str_contains_case_insensitive(filename, "colorcycle")) {
			s_colorcycle_offset = 0;
			spdlog::info("Providing in-memory fallback handle for {}", filename);
			return FAKE_HANDLE_COLORCYCLE;
		}
		if (str_contains_case_insensitive(filename, "popcycle")) {
			spdlog::info("Providing in-memory fallback handle for {}", filename);
			return FAKE_HANDLE_POPCYCLE;
		}
	}
	return handle;
}

#ifdef __aarch64__
DECL_HOOK(uint32_t, CFileMgr_CloseFile, uintptr_t handle)
#else
DECL_HOOK(uint32_t, CFileMgr_CloseFile, uint32_t handle)
#endif
{
	if (handle == FAKE_HANDLE_TIMECYC || handle == FAKE_HANDLE_COLORCYCLE || handle == FAKE_HANDLE_POPCYCLE) {
		spdlog::info("CFileMgr::CloseFile: closed fallback handle 0x{:x}", handle);
		return 0;
	}
	if (!handle) return 0;
	return CFileMgr_CloseFile(handle);
}

DECL_HOOK(void, SurfaceInfos_c_LoadAdhesiveLimits, uintptr_t _this)
{
	// Pre-populate with standard GTA:SA default adhesive limits
	float* matrix = reinterpret_cast<float*>(_this);
	static const float defaultLimits[6][6] = {
		{6.0f, 3.6f, 4.5f, 3.2f, 2.0f, 2.4f},
		{3.6f, 2.0f, 3.0f, 3.5f, 4.0f, 2.0f},
		{4.5f, 3.0f, 6.0f, 2.0f, 2.0f, 1.0f},
		{3.2f, 3.5f, 2.0f, 1.0f, 1.0f, 1.0f},
		{2.0f, 4.0f, 2.0f, 1.0f, 1.0f, 1.0f},
		{2.4f, 2.0f, 1.0f, 1.0f, 1.0f, 0.5f}
	};
	if (matrix) {
		for (int i = 0; i < 6; ++i) {
			for (int j = 0; j < 6; ++j) {
				matrix[i * 6 + j] = defaultLimits[i][j];
			}
		}
	}

	if (!find_real_file("SAMP/surface.dat").empty() || !find_real_file("data/surface.dat").empty()) {
		SurfaceInfos_c_LoadAdhesiveLimits(_this);
		spdlog::info("SurfaceInfos_c::LoadAdhesiveLimits initialized from surface.dat");
	} else {
		spdlog::info("SurfaceInfos_c::LoadAdhesiveLimits initialized successfully with default matrix");
	}
}

DECL_HOOK(void, CTimeCycle_Initialise, bool a1)
{
	spdlog::info("CTimeCycle::Initialise({}) called", a1);
	CTimeCycle_Initialise(a1);
	spdlog::info("CTimeCycle::Initialise finished successfully");
}

static std::string normalize_forward_slashes(const char* raw)
{
	if (!raw) return "";
	std::string out(raw);
	for (char& c : out) {
		if (c == '\\') c = '/';
	}
	return out;
}

DECL_HOOK(int, OS_FileOpen, int a1, uintptr_t handle, char *name, int a2)
{
	char path[512] = {0};

	if (!name) goto ret;

	{
		struct DataRedirect {
			const char* matchRel;
			const char* bareAlias;
			const char* sampRel;
			bool modDataBeatsSamp;
		};
		static const DataRedirect kRedirects[] = {
			{"data/surface.dat",       nullptr,         "SAMP/surface.dat",    false},
			{"data/surfinfo.dat",      nullptr,         "SAMP/surfinfo.dat",   false},
			{"data/surfaud.dat",       nullptr,         "SAMP/surfaud.dat",    false},
			{"data/script/mainv1.scm", nullptr,         "SAMP/main.scm",       false},
			{"data/script/scriptv1.img", nullptr,       "SAMP/script.img",     false},
			{"data/gta.dat",           nullptr,         "SAMP/gta.dat",        false},
			{"data/peds.ide",          nullptr,         "SAMP/peds.ide",       false},
			{"data/samp.ide",          "samp.ide",      "SAMP/SAMP.ide",       false},
			{"data/paths/tracks2.dat", nullptr,         "SAMP/tracks2.dat",    true},
			{"data/paths/tracks4.dat", nullptr,         "SAMP/tracks4.dat",    true},
			{"data/timecyc.dat",       "timecyc.dat",   "SAMP/timecyc.dat",    true},
			{"data/colorcycle.dat",    "colorcycle.dat","SAMP/colorcycle.dat", true},
			{"data/popcycle.dat",      "popcycle.dat",  "SAMP/popcycle.dat",   true},
			{"data/handling.cfg",      "handling.cfg",  "SAMP/handling.cfg",   true},
			{"data/vehicles.ide",      "vehicles.ide",  "SAMP/vehicles.ide",   true},
			{"data/weapon.dat",        "weapon.dat",    "SAMP/weapon.dat",     true},
			{"data/carcols.dat",       "carcols.dat",   "SAMP/carcols.dat",    true},
			{"data/object.dat",        "object.dat",    "SAMP/object.dat",     true},
		};

		std::string reqLower = to_lower_ascii(normalize_forward_slashes(name));
		while (!reqLower.empty() && reqLower.front() == '/') {
			reqLower.erase(reqLower.begin());
		}

		for (const auto& rule : kRedirects) {
			bool matched = (reqLower == rule.matchRel) ||
				(rule.bareAlias && reqLower == rule.bareAlias);
			if (!matched) continue;

			std::string chosen;
			bool modBeatsSamp = rule.modDataBeatsSamp ||
				(Settings::modifieddata() &&
				 strcmp(rule.matchRel, "data/script/mainv1.scm") != 0 &&
				 strcmp(rule.matchRel, "data/script/scriptv1.img") != 0 &&
				 strcmp(rule.matchRel, "data/gta.dat") != 0);
			{
				std::lock_guard<std::mutex> lock(s_fsCacheMutex);
				init_file_bases_locked();
				if (modBeatsSamp) {
					// Modpacks under mods/ override everything first.
					chosen = resolve_in_base_list(s_modBases, rule.sampRel);
					if (chosen.empty()) chosen = resolve_in_base_list(s_modBases, rule.matchRel);
					// External game directory data/ or SAMP/ override internal /data/data/ defaults.
					if (chosen.empty()) {
						const char* gd = Client::gameDir();
						std::string extSamp = (gd && gd[0]) ? resolve_case_insensitive_path(gd, rule.sampRel) : "";
						std::string extData = (gd && gd[0]) ? resolve_case_insensitive_path(gd, rule.matchRel) : "";
						bool preferDataOverSamp = Settings::modifieddata() ||
							!strcasecmp(rule.matchRel, "data/timecyc.dat") ||
							!strcasecmp(rule.matchRel, "data/colorcycle.dat") ||
							!strcasecmp(rule.matchRel, "data/popcycle.dat");
						chosen = preferDataOverSamp
							? (!extData.empty() ? extData : extSamp)
							: (!extSamp.empty() ? extSamp : extData);
					}
				} else {
					chosen = resolve_in_base_list(s_modBases, rule.sampRel);
					if (chosen.empty()) chosen = resolve_in_base_list(s_modBases, rule.matchRel);
				}
			}
			if (chosen.empty()) {
				chosen = modBeatsSamp
					? (!find_real_file(rule.matchRel).empty() ? find_real_file(rule.matchRel) : find_real_file(rule.sampRel))
					: (!find_real_file(rule.sampRel).empty() ? find_real_file(rule.sampRel) : find_real_file(rule.matchRel));
			}

			// Protect against partial/stub SAMP/peds.ide overriding full vanilla data/peds.ide
			if (!strcasecmp(rule.matchRel, "data/peds.ide") && !chosen.empty() && str_contains_case_insensitive(chosen.c_str(), "samp/peds.ide")) {
				struct stat sampSt{};
				std::string vanillaPeds = find_real_file("data/peds.ide");
				if (!vanillaPeds.empty() && stat(chosen.c_str(), &sampSt) == 0 && sampSt.st_size < 4096) {
					struct stat vanSt{};
					if (stat(vanillaPeds.c_str(), &vanSt) == 0 && vanSt.st_size >= 4096) {
						chosen = vanillaPeds;
					}
				}
			}

			if (!chosen.empty()) {
				std::string relName = to_storage_relative(chosen);
				if (!relName.empty()) {
					snprintf(path, sizeof(path), "%s", relName.c_str());
					name = path;
				} else if (a1 != 1 && a2 != 1 && a2 != 2 && chosen.front() == '/') {
					snprintf(path, sizeof(path), "%s", chosen.c_str());
					name = path;
				}
				spdlog::debug("OS_FileOpen redirect '{}' -> '{}'", reqLower, name);
			}
			goto call_orig;
		}
	}

ret:
	{
		char* storageRoot = g_saSym ? g_saSym->GetSymbol<char*>("StorageRootBuffer") : nullptr;
		if (storageRoot && Client::gameDir() && Client::gameDir()[0] && strcmp(storageRoot, Client::gameDir()) != 0) {
			snprintf(storageRoot, 512, "%s", Client::gameDir());
		}

		bool isReadOnly = (a2 != 1 && a2 != 2);
		std::string real = find_real_file(name, isReadOnly);
		std::string relName = !real.empty() ? to_storage_relative(real) : "";
		if (!relName.empty()) {
			snprintf(path, sizeof(path), "%s", relName.c_str());
			name = path;
		} else if (!real.empty() && isReadOnly && a1 != 1 && real.front() == '/') {
			snprintf(path, sizeof(path), "%s", real.c_str());
			name = path;
		} else {
			std::string norm = normalize_forward_slashes(name);
			if (norm != name) {
				snprintf(path, sizeof(path), "%s", norm.c_str());
				name = path;
			}
		}
	}

call_orig:
	return OS_FileOpen(a1, handle, name, a2);
}

DECL_HOOK(sa::CPedModelInfo *, CModelInfo_AddPedModel, int id)
{
	if (id < 0 || id >= 20000) {
		spdlog::error("CModelInfo_AddPedModel: out-of-range id {}", id);
		return nullptr;
	}

	auto model = new sa::CPedModelInfo{};
	model->vtable = g_saSym->GetVmtFunction<uintptr_t>("_ZTV13CPedModelInfo", 1, true); // CPedModelInfo::~CPedModelInfo

	// CClumpModelInfo::Init (calls CBaseModelInfo::Init)
	model->Init();

	auto ms_modelInfoPtrs = g_saSym->Abs<sa::CBaseModelInfo **>(addr::CModelInfo_ms_modelInfoPtrs);
	ms_modelInfoPtrs[id] = model;
	return model;
}

DECL_HOOK(sa::CAtomicModelInfo *, CModelInfo_AddAtomicModel, int id)
{
	// spdlog::info("Adding atomic model: {}", id);
	auto model = new sa::CAtomicModelInfo{};
	model->vtable = g_saSym->GetVmtFunction<uintptr_t>("_ZTV16CAtomicModelInfo", 1, true); // CAtomicModelInfo::~CAtomicModelInfo

	typedef sa::CStore<sa::CAtomicModelInfo, 14000> AtomicModelStore;
	auto ms_atomicModelStore = g_saSym->Abs<AtomicModelStore *>(addr::ms_atomicModelStore);
	ms_atomicModelStore->AddItem();

	// CAtomicModelInfo::Init
	model->Init();

	// CModelInfo::ms_modelInfoPtrs
	auto ms_modelInfoPtrs = g_saSym->Abs<sa::CBaseModelInfo **>(addr::CModelInfo_ms_modelInfoPtrs);
	ms_modelInfoPtrs[id] = model;
	return model;
}

static bool tdb_thumb_exists(const char* dbName, const char* ext)
{
	if (!dbName || !ext) return false;
	std::string rel = std::string("texdb/") + dbName + "/" + dbName + "." + ext + ".tmb";
	return !find_real_file(rel, true).empty();
}

static bool tdb_any_format_exists(const char* dbName)
{
	static const char* kExts[] = { "etc", "dxt", "pvr", "unc", "360", "ps3" };
	for (const char* ext : kExts) {
		if (tdb_thumb_exists(dbName, ext)) {
			return true;
		}
	}
	return false;
}

static bool is_builtin_tdb_name(const std::string& lowerName)
{
	static const char* kBuiltin[] = {
		"gta3", "gta_int", "cutscene", "menu", "mobile", "player", "playerhi", "txd", "samp"
	};
	for (const char* b : kBuiltin) {
		if (lowerName == b) return true;
	}
	return false;
}

static void discover_custom_tdbs()
{
	s_customTdbNames.clear();
	for (const auto& base : get_valid_bases()) {
		std::string texdbDir;
		DIR* bd = opendir(base.c_str());
		if (!bd) continue;
		struct dirent* be;
		while ((be = readdir(bd)) != nullptr) {
			if (strcasecmp(be->d_name, "texdb") == 0) {
				texdbDir = base + be->d_name + "/";
				break;
			}
		}
		closedir(bd);
		if (texdbDir.empty()) continue;

		DIR* td = opendir(texdbDir.c_str());
		if (!td) continue;
		struct dirent* te;
		while ((te = readdir(td)) != nullptr) {
			if (te->d_name[0] == '.') continue;
			std::string sub = texdbDir + te->d_name;
			struct stat st{};
			if (stat(sub.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) continue;

			std::string lower = to_lower_ascii(te->d_name);
			if (is_builtin_tdb_name(lower)) continue;

			bool dup = false;
			for (const auto& existing : s_customTdbNames) {
				if (to_lower_ascii(existing) == lower) {
					dup = true;
					break;
				}
			}
			if (!dup && tdb_any_format_exists(te->d_name)) {
				s_customTdbNames.emplace_back(te->d_name);
			}
		}
		closedir(td);
	}
}

// GTA:SA v2.10 TextureDatabaseFormat mapping: 0=unc, 1=dxt, 2=360, 3=ps3, 4=pvr, 5=etc, 6=DF_Default
DECL_HOOK(sa::TextureDatabaseRuntime*, TextureDatabaseRuntime_Load, const char* dbName, bool bFullLoad, int format)
{
	static const char* kExts[6] = { "unc", "dxt", "360", "ps3", "pvr", "etc" };
	static const int kGpuOrder[3] = { 1, 4, 5 }; // dxt, pvr, etc
	static const int kAllOrder[6] = { 5, 1, 4, 0, 2, 3 };

	if (!dbName || !dbName[0]) return nullptr;

	if (format >= 0 && format < 6) {
		if (!tdb_thumb_exists(dbName, kExts[format])) {
			int fallback = -1;
			for (int fmt : kAllOrder) {
				if (tdb_thumb_exists(dbName, kExts[fmt])) {
					fallback = fmt;
					break;
				}
			}
			if (fallback == -1) {
				spdlog::warn("TextureDatabaseRuntime::Load skipped missing DB '{}' (format={})", dbName, format);
				return nullptr;
			}
			spdlog::info("TextureDatabaseRuntime::Load fallback for '{}': {} -> {}", dbName, format, fallback);
			format = fallback;
		}
	} else if (format == sa::DF_Default) {
		int foundCount = 0;
		int foundFmt = -1;
		for (int fmt : kGpuOrder) {
			if (tdb_thumb_exists(dbName, kExts[fmt])) {
				foundFmt = fmt;
				foundCount++;
			}
		}
		if (foundCount == 1) {
			format = foundFmt;
		} else if (foundCount == 0) {
			if (tdb_thumb_exists(dbName, "unc")) format = sa::DF_UNC;
			else if (tdb_thumb_exists(dbName, "ps3")) format = sa::DF_PS3;
			else if (tdb_thumb_exists(dbName, "360")) format = sa::DF_360;
			else {
				spdlog::warn("TextureDatabaseRuntime::Load skipped missing auto DB '{}'", dbName);
				return nullptr;
			}
		}
	}

	return TextureDatabaseRuntime_Load(dbName, bFullLoad, format);
}

DECL_HOOK(RwTexture *, CTxdStore_TxdStoreFindCB, const char *texture_name)
{
	std::vector<std::string> texture_databases;
	for (const auto& customDb : s_customTdbNames) {
		texture_databases.push_back(customDb);
	}
	static const char* kStandardDbs[] = {
		"samp", "gta_int", "gta3", "txd", "menu", "mobile", "player", "playerhi"
	};
	for (const char* db : kStandardDbs) {
		texture_databases.emplace_back(db);
	}

	for (const auto &texture_database : texture_databases)
	{
		auto database_handle = sa::TextureDatabaseRuntime::GetDatabase(texture_database.c_str());
		if (!database_handle)
		{
			continue;
		}

		auto& registered = sa::TextureDatabaseRuntime::registered();
		bool alreadyRegistered = false;
		if (registered.dataPtr)
		{
			for (uint32_t index = 0; index < registered.numEntries; ++index)
			{
				if (registered.dataPtr[index] == database_handle)
				{
					alreadyRegistered = true;
					break;
				}
			}
		}

		if (!alreadyRegistered)
		{
			sa::TextureDatabaseRuntime::Register(database_handle);
			RwTexture *texture = sa::TextureDatabaseRuntime::GetTexture(texture_name);
			sa::TextureDatabaseRuntime::Unregister(database_handle);

			if (texture)
			{
				return texture;
			}
		}
	}

	RwTexDictionary *current = Memory::callFunction<RwTexDictionary *>("_Z25RwTexDictionaryGetCurrentv");
	while (current)
	{
		RwTexture *texture = Memory::callFunction<RwTexture *>("_Z31RwTexDictionaryFindNamedTextureP15RwTexDictionaryPKc", current, texture_name);
		if (texture)
		{
			return texture;
		}

		current = sa::CTxdStore::GetTxdParent(current);
	}

	return nullptr;
}

DECL_HOOK(void, ValidateVersion)
{
	spdlog::info("ValidateVersion called, bypassing anti-tamper check");
	auto pVersion = (char*)g_saSym->GetSymbol("version_name");
	if (pVersion) {
		strncpy(pVersion, "v2.10", 32);
	}
}

DECL_HOOK(int, CGame_InitialiseRenderWare)
{
	spdlog::info("CGame_InitialiseRenderWare called, executing original...");

	int result = CGame_InitialiseRenderWare();
	spdlog::info("CGame_InitialiseRenderWare original returned: {}", result);

	spdlog::info("Loading samp texture database...");
	auto sampTex = sa::TextureDatabaseRuntime::Load("samp", false, sa::DF_Default);
	spdlog::info("samp texture database loaded: {}", (void*)sampTex);

	discover_custom_tdbs();
	for (const auto& customDb : s_customTdbNames) {
		auto customHandle = sa::TextureDatabaseRuntime::Load(customDb.c_str(), false, sa::DF_Default);
		spdlog::info("Loaded custom texture database '{}': {}", customDb, (void*)customHandle);
	}

	Client::initializeUI();
	spdlog::info("CGame_InitialiseRenderWare hook completed");
	return result;
}

#ifdef __aarch64__
static const size_t RSGLOBAL_QUIT_OFFSET = 0x14;
#else
static const size_t RSGLOBAL_QUIT_OFFSET = 0x10;
#endif

DECL_HOOK(int, CGame_InitialiseEssentialsAfterRW)
{
	spdlog::info("CGame_InitialiseEssentialsAfterRW called, executing original...");
	int result = CGame_InitialiseEssentialsAfterRW();
	spdlog::info("CGame_InitialiseEssentialsAfterRW original returned: {}", result);

	uintptr_t pRsGlobal = (uintptr_t)g_saSym->GetSymbol<void*>("RsGlobal");
	if (pRsGlobal) {
		uint32_t* pQuit = reinterpret_cast<uint32_t*>(pRsGlobal + RSGLOBAL_QUIT_OFFSET);
		if (*pQuit != 0) {
			spdlog::warn("RsGlobal->quit was {}! Resetting to 0 to prevent game exit", *pQuit);
			*pQuit = 0;
		}
	}

	return 1;
}

DECL_HOOK(void, CPopCycle_Initialise)
{
	spdlog::info("CPopCycle::Initialise called, executing original...");
	CPopCycle_Initialise();
	spdlog::info("CPopCycle::Initialise finished successfully");
}

DECL_HOOK(int, CGame_InitialiseCoreDataAfterRW)
{
	spdlog::info("CGame_InitialiseCoreDataAfterRW called, executing original...");
	int result = CGame_InitialiseCoreDataAfterRW();
	spdlog::info("CGame_InitialiseCoreDataAfterRW original returned: {}", result);

	uintptr_t pRsGlobal = (uintptr_t)g_saSym->GetSymbol<void*>("RsGlobal");
	if (pRsGlobal) {
		uint32_t* pQuit = reinterpret_cast<uint32_t*>(pRsGlobal + RSGLOBAL_QUIT_OFFSET);
		if (*pQuit != 0) {
			spdlog::warn("RsGlobal->quit was {} in CoreData! Resetting to 0 to prevent game exit", *pQuit);
			*pQuit = 0;
		}
	}

	return 1;
}

struct CStreamingFile
{
	unsigned char m_name[40];
	bool m_bRegister;
	uint8_t m_padding[3];
	int32_t m_lsn;
};
VALIDATE_SIZE(CStreamingFile, 0x30);

CStreamingFile *CStreaming_ms_files = nullptr;
static void try_add_streaming_img(const char* imgRel, int& addedCount, std::vector<std::string>& seenImgs)
{
	if (!imgRel || addedCount >= 8) return;
	if (strlen(imgRel) >= sizeof(CStreamingFile::m_name)) {
		spdlog::warn("Skipping IMG path exceeding 39 chars: {}", imgRel);
		return;
	}
	std::string normLower = to_lower_ascii(normalize_forward_slashes(imgRel));
	for (const auto& s : seenImgs) {
		if (s == normLower) return;
	}
	if (find_real_file(imgRel, true).empty()) return;

	Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", imgRel, true);
	seenImgs.push_back(std::move(normLower));
	addedCount++;
	spdlog::info("Added IMG archive slot #{}: {}", addedCount, imgRel);
}

DECL_HOOK(void, CStreaming_InitImageList)
{
	spdlog::info("Initializing image list...");

	CStreaming_ms_files = g_saSym->GetSymbol<CStreamingFile *>("_ZN10CStreaming8ms_filesE");

	if (CStreaming_ms_files)
	{
		for (int i = 0; i < 8; i++)
		{
			CStreaming_ms_files[i].m_name[0] = '\0';
			CStreaming_ms_files[i].m_lsn = 0;
		}

		int addedCount = 0;
		std::vector<std::string> seenImgs;
		auto addRequired = [&](const char* img) {
			Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", img, true);
			seenImgs.push_back(to_lower_ascii(normalize_forward_slashes(img)));
			addedCount++;
		};

		addRequired("TEXDB\\SAMPCOL.IMG");
		addRequired("TEXDB\\GTA3.IMG");
		addRequired("TEXDB\\GTA_INT.IMG");
		addRequired("TEXDB\\SAMP.IMG");

		// Optional modpack / mobile archives (ms_files fits at most 8 slots total)
		const char* extraCandidates[] = {
			"TEXDB\\PLAYER.IMG",
			"TEXDB\\CUSTOM.IMG",
			"SAMP\\CUSTOM.IMG",
			"MODS\\CUSTOM.IMG",
			"TEXDB\\MODPACK.IMG",
			"MODS\\MODPACK.IMG",
			"MODS\\MODS.IMG"
		};
		for (const char* cand : extraCandidates) {
			try_add_streaming_img(cand, addedCount, seenImgs);
		}
	}
	else
	{
		spdlog::error("Failed to get symbol for CStreaming_ms_files!");
	}
}

DECL_HOOK(void, CStreaming_Init2)
{
	CStreaming_Init2();

	// CStreaming::ms_memoryAvailable
	*g_saSym->GetSymbol<uint32_t *>("_ZN10CStreaming18ms_memoryAvailableE") *= 3;
}

DECL_HOOK(void, CTimer_StartUserPause)
{
	if (pUI)
	{
		pUI->setVisible(false);
	}

	if (g_java && pNetGame)
	{
		g_java->setPauseState(true);
	}

	*g_saSym->GetSymbol<bool *>("_ZN6CTimer11m_UserPauseE") = true;
}

DECL_HOOK(void, CTimer_EndUserPause)
{
	if (pUI)
	{
		pUI->setVisible(true);
	}

	if (g_java && pNetGame)
	{
		g_java->setPauseState(false);
	}

	*g_saSym->GetSymbol<bool *>("_ZN6CTimer11m_UserPauseE") = false;
}

unsigned int *msCollected;
unsigned int curMS;

DECL_HOOK(void, Render2dStuff)
{
	Render2dStuff();

	Client::process();

	if (Settings::displayfps())
	{
		unsigned int nowMs = Memory::callFunction<unsigned int>("_Z9OS_TimeMSv");
		msCollected[curMS++ % 0x28] = nowMs;

		static unsigned int s_lastFpsUpdateMs = 0;
		static unsigned int s_lastMemUpdateMs = 0;
		static char s_szStrFps[64] = "FPS: 0.00";
		static char s_szStrMem[64] = "MEM: 0 / 0 MB";
		static const char* s_szStrArch = VER_x32 ? "ARCH: x32" : "ARCH: x64";

		if (curMS >= 0x28 && (nowMs - s_lastFpsUpdateMs) >= 200) {
			unsigned int oldest = msCollected[curMS % 0x28];
			unsigned int newest = msCollected[(curMS - 1) % 0x28];
			unsigned int delta = newest - oldest;
			if (delta > 0) {
				snprintf(s_szStrFps, sizeof(s_szStrFps), "FPS: %.2f", 39000.0f / static_cast<float>(delta));
			}
			s_lastFpsUpdateMs = nowMs;
		}

		// Throttle expensive heap-walk (mallinfo2) & system memory queries to 1Hz instead of every frame
		if ((nowMs - s_lastMemUpdateMs) >= 1000 || s_lastMemUpdateMs == 0) {
			struct mallinfo2 memInfo = mallinfo2();
			long long totalAllocatedMB = static_cast<long long>(memInfo.uordblks) / (1024 * 1024);
			static long long s_totalMemoryMB = 0;
			if (s_totalMemoryMB == 0) {
#if VER_x32
				FILE *meminfo = fopen("/proc/meminfo", "r");
				if (meminfo) {
					char line[256];
					while (fgets(line, sizeof(line), meminfo)) {
						if (strncmp(line, "MemTotal:", 9) == 0) {
							s_totalMemoryMB = atoll(&line[9]) / 1024;
							break;
						}
					}
					fclose(meminfo);
				}
#else
				struct sysinfo info{};
				if (sysinfo(&info) == 0) {
					s_totalMemoryMB = static_cast<long long>(info.totalram) / (1024 * 1024);
				}
#endif
			}
			snprintf(s_szStrMem, sizeof(s_szStrMem), "MEM: %lld / %lld MB", totalAllocatedMB, s_totalMemoryMB);
			s_lastMemUpdateMs = nowMs;
		}

		RGBA bgColor = {0, 0, 0, 0};
		RGBA color = {255, 255, 255, 255};
		const float baseScaleX = 1.12f * 0.54f;
		const float baseScaleY = 1.12f;
		const float drawX = (static_cast<float>(sa::RsGlobal->screenWidth) / 640.0f) * 4.0f;
		const float scaleH = static_cast<float>(sa::RsGlobal->screenHeight) / 448.0f;

		CFont::SetBackgroundColor(&bgColor);
		CFont::SetScale(baseScaleX, baseScaleY);
		CFont::SetOrientation(1);
		CFont::SetJustify(0);
		CFont::SetWrapX(640.0);
		CFont::SetProportional(0);
		CFont::SetFontStyle(1);
		CFont::SetEdge(0);
		CFont::SetColor(&color);

		CFont::PrintString(drawX, scaleH * 200.0f, s_szStrFps);
		CFont::PrintString(drawX, scaleH * 210.0f, s_szStrMem);
		CFont::PrintString(drawX, scaleH * 220.0f, const_cast<char*>(s_szStrArch));
	}
}

DECL_HOOK(uint32_t, Idle, void *param, bool firstFrame)
{
	uint32_t result = Idle(param, firstFrame);

	if (pUI)
	{
		pUI->render();
	}

	if (sa::Scene && sa::Scene->m_pRwCamera)
	{
		RwCameraEndUpdate(sa::Scene->m_pRwCamera);
	}

	return result;
}

DECL_HOOK(void, CRadar_ClearBlip, int32_t nIndex)
{
	uintptr_t dwRetAddr = 0;
	GET_LR(dwRetAddr);

	if ((uint16_t)nIndex > 249)
	{
		spdlog::warn("Invalid blip ID ({}). Called from 0x{:X}", nIndex, dwRetAddr);
		return;
	}

	return CRadar_ClearBlip(nIndex);
}

DECL_HOOK(void, CPools_Initialise)
{
	spdlog::info("GTA pools initializing..");
	Patches::initBetterPoolsSize();
}

ThreadOptimizer g_threadOpt;
DECL_HOOK(void, AND_RunThread, void *a1)
{
	g_threadOpt.pushThread(gettid());
	AND_RunThread(a1);
}

extern sa::CPlayerInfo *WORLD_PLAYERS;

DECL_HOOK(sa::CPlayerInfo *, GetPlayerInfoForThisPlayerPed, sa::CPed *ped)
{
	if (!WORLD_PLAYERS) return nullptr;
	for (int i = 0; i < PLAYER_PED_SLOTS; i++)
	{
		if (WORLD_PLAYERS[i].m_pPed == ped)
		{
			return &WORLD_PLAYERS[i];
		}
	}

	return nullptr;
}

DECL_HOOK(uint64_t *, RQCommand_rqSetAlphaTest, uint64_t *result)
{
	*result += 8;
	return result;
}

DECL_HOOK(int, GetInputType)
{
	return 0;
}

DECL_HOOK(void, CWidget, uintptr_t pWidget, const char *pszSprite, uintptr_t WidgetPos, unsigned int nFlags, uint32_t Mapping)
{
	spdlog::info("Widget: {}", pszSprite);
	CWidget(pWidget, pszSprite, WidgetPos, nFlags, Mapping);
}

DECL_HOOK(void, CWidget_Update, uintptr_t pWidget)
{
	if (pNetGame)
	{
		switch (ProcessFixedWidget(pWidget))
		{
		case STATE_NONE:
			break;
		case STATE_DISABLED:
			return;
		}
	}
	CWidget_Update(pWidget);
}

DECL_HOOK(void, CWidget_SetEnabled, uintptr_t pWidget, bool bEnabled)
{
	if (pNetGame)
	{
		switch (ProcessFixedWidget(pWidget))
		{
		case STATE_NONE:
			break;
		case STATE_DISABLED:
			bEnabled = false;
			break;
		}
	}
	CWidget_SetEnabled(pWidget, bEnabled);
}

DECL_HOOK(int, CWorld_FindPlayerSlotWithPedPointer, sa::CPed *pPlayersPed)
{
	if (!WORLD_PLAYERS) return -1;
	for (int i = 0; i < PLAYER_PED_SLOTS; ++i)
	{
		if (WORLD_PLAYERS[i].m_pPed == pPlayersPed)
		{
			return i;
		}
	}
	return -1;
}

int g_lastObjectModel = -1;

DECL_HOOK(int32_t, CObject_Render, sa::CObject *_this)
{
	if (_this)
	{
		g_lastObjectModel = _this->m_nModelIndex;
	}

	// object material
	if (_this && !IsEntityPlaceable(_this))
	{
		if (pNetGame)
		{
			CObject *pObject = pNetGame->GetObjectPool()->FindObjectFromGtaPtr(_this);
			if (pObject)
			{
				pObject->TryChangeToCustomObjectMaterial();
			}
		}
	}

	int32_t result = CObject_Render(_this);

	if (_this && !IsEntityPlaceable(_this))
	{
		if (pNetGame)
		{
			CObject *pObject = pNetGame->GetObjectPool()->FindObjectFromGtaPtr(_this);
			if (pObject)
			{
				pObject->ChangeToOriginalObjectMaterial();
			}
		}
	}

	return result;
}

DECL_HOOK(void, CRenderer_RenderEverythingBarRoads)
{
	CRenderer_RenderEverythingBarRoads();

	if (pNetGame)
	{
		CObjectPool *pObjectPool = pNetGame->GetObjectPool();
		if (pObjectPool)
		{
			for (OBJECTID i = 0; i < MAX_OBJECTS; i++)
			{
				CObject *pObject = pObjectPool->GetAt(i);
				if (pObject && pObject->m_bForceRender)
				{
					pObject->Render();
				}
			}
		}
	}
}

DECL_HOOK(void, CWorld_ProcessPedsAfterPreRender)
{
	CWorld_ProcessPedsAfterPreRender();

	if (pNetGame)
	{
		CPlayerPool *pPlayerPool = pNetGame->GetPlayerPool();
		if (pPlayerPool)
		{
			pPlayerPool->ProcessAttachedObjects();
		}
	}
}

DECL_HOOK(int, CustomPipeRenderCB, RwResEntry *resEntry, uintptr_t object, uint8_t type, uint32_t flags)
{
	if (!resEntry || !object)
	{
		spdlog::warn("CustomPipeRenderCB: Prevent crash - resEntry or object is null");
		return 0;
	}

	return CustomPipeRenderCB(resEntry, object, type, flags);
}

DECL_HOOK(int, rxOpenGLDefaultAllInOneRenderCB, RwResEntry *resEntry, uintptr_t object, uint8_t type, uint32_t flags)
{
	if (!resEntry || !object)
	{
		spdlog::warn("rxOpenGLDefaultAllInOneRenderCB: Prevent crash - resEntry or object is null");
		return 0;
	}

	return rxOpenGLDefaultAllInOneRenderCB(resEntry, object, type, flags);
}

DECL_HOOK(RwBool, _RwTextureDestroy, uintptr_t texture)
{
	if (!texture)
	{
		spdlog::warn("RwTextureDestroy: Prevent crash");
		return 0;
	}
	return _RwTextureDestroy(texture);
}

DECL_HOOK(uintptr_t, RwFrameAddChild, uintptr_t a1, uintptr_t a2)
{
	if (!a1 || !a2)
	{
		spdlog::warn("RwFrameAddChild: Prevent crash");
		return 0;
	}
	return RwFrameAddChild(a1, a2);
}

DECL_HOOK(uintptr_t, RLEDecompress, uintptr_t a1, unsigned int a2, const char *a3, unsigned int a4, unsigned int a5)
{
	if (!a1 || !a3)
	{
		spdlog::warn("RLEDecompress: Prevent crash");
		return 0;
	}
	return RLEDecompress(a1, a2, a3, a4, a5);
}

DECL_HOOK(RwBool, RwResourcesFreeResEntry, uintptr_t a1)
{
	if (!a1)
	{
		spdlog::warn("RwResourcesFreeResEntry: Prevent crash");
		return 0;
	}
	return RwResourcesFreeResEntry(a1);
}

DECL_HOOK(int, TextureDatabaseRuntime_GetEntry, uintptr_t _this, const char *a2, bool *a3)
{
	if (!_this)
	{
		spdlog::warn("TextureDatabaseRuntime_GetEntry: Prevent crash");
		return -1;
	}
	return TextureDatabaseRuntime_GetEntry(_this, a2, a3);
}

static bool can_open_mobile_pause_menu()
{
	static bool s_checked = false;
	static bool s_supported = false;
	if (s_checked) {
		return s_supported;
	}
	if (!TextureDatabaseRuntime_GetEntry) {
		return false;
	}

	auto* menuDb = sa::TextureDatabaseRuntime::GetDatabase("menu");
	if (!menuDb) {
		menuDb = sa::TextureDatabaseRuntime::Load("menu", false, sa::DF_Default);
	}
	auto* mobileDb = sa::TextureDatabaseRuntime::GetDatabase("mobile");
	if (!menuDb && !mobileDb) {
		spdlog::warn("MobileMenu: 'menu'/'mobile' texture database unavailable, pause menu disabled safely");
		s_checked = true;
		s_supported = false;
		return false;
	}

	const char* requiredTextures[] = {
		"menu_selector",
		"menu_bgmap",
		"menu_sliderempty",
		"menu_sliderfull",
		"menu_slidernub",
		"menu_adjback",
		"menu_adjback2"
	};

	bool allFound = true;
	bool needMobileRegistered = false;
	for (const char* texName : requiredTextures) {
		bool isAlpha = false;
		int idx = menuDb ? TextureDatabaseRuntime_GetEntry(reinterpret_cast<uintptr_t>(menuDb), texName, &isAlpha) : -1;
		if (idx < 0 && mobileDb) {
			idx = TextureDatabaseRuntime_GetEntry(reinterpret_cast<uintptr_t>(mobileDb), texName, &isAlpha);
			if (idx >= 0) {
				needMobileRegistered = true;
			}
		}
		if (idx < 0) {
			spdlog::warn("MobileMenu: missing '{}' in menu/mobile texdb, pause menu disabled safely", texName);
			allFound = false;
			break;
		}
	}

	if (allFound && needMobileRegistered && mobileDb) {
		auto& reg = sa::TextureDatabaseRuntime::registered();
		bool already = false;
		if (reg.dataPtr) {
			for (uint32_t i = 0; i < reg.numEntries; ++i) {
				if (reg.dataPtr[i] == mobileDb) {
					already = true;
					break;
				}
			}
		}
		if (!already) {
			sa::TextureDatabaseRuntime::Register(mobileDb);
		}
	}

	s_checked = true;
	s_supported = allFound;
	return s_supported;
}

DECL_HOOK(void, MobileMenu_InitForTitle, uintptr_t _this)
{
	spdlog::info("MobileMenu::InitForTitle called - safely bypassing single-player title menu");
}

DECL_HOOK(void, MobileMenu_Load, uintptr_t _this)
{
	if (!can_open_mobile_pause_menu()) {
		spdlog::warn("MobileMenu::Load bypassed (missing menu textures)");
		return;
	}
	MobileMenu_Load(_this);
}

DECL_HOOK(void, MobileMenu_InitForPause, uintptr_t _this)
{
	if (!can_open_mobile_pause_menu()) {
		if (auto* pUserPause = g_saSym->GetSymbol<bool*>("_ZN6CTimer11m_UserPauseE")) {
			*pUserPause = false;
		}
		if (auto* pCodePause = g_saSym->GetSymbol<bool*>("_ZN6CTimer11m_CodePauseE")) {
			*pCodePause = false;
		}
		return;
	}
	MobileMenu_InitForPause(_this);
}

DECL_HOOK(void*, TextureDatabaseRuntime_GetTexture, const char* name)
{
	if (!name) return nullptr;

	auto pRegistered = g_saSym->GetSymbol<sa::TDBArray<uintptr_t>*>("_ZN22TextureDatabaseRuntime10registeredE");
	if (!pRegistered || pRegistered->numEntries == 0 || !pRegistered->dataPtr) {
		return nullptr;
	}

	for (uint32_t i = 0; i < pRegistered->numEntries; i++) {
		if (!pRegistered->dataPtr[i]) {
			spdlog::warn("TextureDatabaseRuntime::GetTexture(\"{}\"): registered.dataPtr[{}] is NULL, returning nullptr", name, i);
			return nullptr;
		}
	}

	return TextureDatabaseRuntime_GetTexture(name);
}

struct AnimBlendClumpDataView
{
#ifdef __aarch64__
	uintptr_t ptr0;
	uintptr_t ptr1;
	int32_t m_nNumBones;
	int32_t pad14;
	uintptr_t ptr18;
	void* m_pFrames;
#else
	uintptr_t ptr0;
	uintptr_t ptr1;
	int32_t m_nNumBones;
	uintptr_t ptr0c;
	void* m_pFrames;
#endif
};

struct RpAnimUpdateParams
{
	uint32_t bHasNonPartial;
	void* nodes[12];
};

static thread_local int s_inUpdateAnimsDepth = 0;

DECL_HOOK(void, CAnimBlendClumpData_ForAllFrames, uintptr_t _this, void (*callback)(void*, void*), void* data)
{
	if (!_this || !callback) {
		return;
	}

	auto* clumpData = reinterpret_cast<AnimBlendClumpDataView*>(_this);
	if (clumpData->m_nNumBones <= 0 || !clumpData->m_pFrames) {
		return;
	}

	if (s_inUpdateAnimsDepth > 0 && data && g_saSym) {
		uintptr_t cbOff = g_saSym->ToLibAddr(reinterpret_cast<uintptr_t>(callback));
#ifdef __aarch64__
		bool isUpdateCb = (cbOff == 0x46621c || cbOff == 0x4649b0 || cbOff == 0x464f10 || cbOff == 0x465448 || cbOff == 0x4659e8);
#else
		uintptr_t thumbOff = cbOff & ~uintptr_t(1);
		bool isUpdateCb = (thumbOff == 0x38c22c || thumbOff == 0x38c6f8 || thumbOff == 0x38cc30 || thumbOff == 0x38d11c || thumbOff == 0x38d93c);
#endif
		if (isUpdateCb) {
			auto* params = reinterpret_cast<RpAnimUpdateParams*>(data);
			if (!params->nodes[0]) {
				return;
			}
		}
	}

	CAnimBlendClumpData_ForAllFrames(_this, callback, data);
}

DECL_HOOK(void, RpAnimBlendClumpUpdateAnimations, void* clump, float step, bool bDoBlend)
{
	if (!clump) {
		return;
	}
	++s_inUpdateAnimsDepth;
	RpAnimBlendClumpUpdateAnimations(clump, step, bDoBlend);
	--s_inUpdateAnimsDepth;
}

DECL_HOOK(void, OALService)
{
	auto pMutex = g_saSym->GetSymbol<pthread_mutex_t**>("trashMutex");
	if (!pMutex || !*pMutex) {
		return;
	}
	OALService();
}

DECL_HOOK(void, CAEFrontendAudioEntity_AddAudioEvent, uintptr_t _this, int eventId, float f1, float f2)
{
	auto ppVolumes = g_saSym->GetSymbol<void**>("_ZN14CAEAudioEntity20m_pAudioEventVolumesE");
	if (!ppVolumes || !*ppVolumes) {
		return;
	}
	CAEFrontendAudioEntity_AddAudioEvent(_this, eventId, f1, f2);
}

DECL_HOOK(void, CGame_Process)
{
	CGame_Process();

	if (pNetGame)
	{
		CTextDrawPool *pTextDrawPool = pNetGame->GetTextDrawPool();
		if (pTextDrawPool)
		{
			pTextDrawPool->SnapshotProcess();
		}

		CObjectPool *pObjectPool = pNetGame->GetObjectPool();
		if (pObjectPool)
		{
			pObjectPool->Process();
			pObjectPool->ProcessMaterialText();
		}
	}
}

DECL_HOOK(int, CRadar_SetCoordBlip, uint32_t BlpType, sa::CVector vecPosition, uint32_t nColour, uint32_t DispFlag, char *pScriptName)
{
	// CWorld::FindGroundZForCoord
	vecPosition.z = Memory::callFunction<float>("_ZN6CWorld19FindGroundZForCoordEff", vecPosition.x, vecPosition.y);

	if (Client::offlineMode())
	{
		RwMatrix mat = pGame->FindPlayerPed()->m_ped->GetMatrix().ToRwMatrix();
		mat.pos = sa::CVector(vecPosition.x, vecPosition.y, vecPosition.z + 5.0f);
		if (pGame->FindPlayerPed()->IsInVehicle())
		{
			pGame->FindPlayerPed()->m_ped->pVehicle->SetMatrix((sa::CMatrix &)mat);
			pGame->FindPlayerPed()->m_ped->SetMatrix((sa::CMatrix &)mat);
		}
		else
		{
			pGame->FindPlayerPed()->m_ped->SetMatrix((sa::CMatrix &)mat);
		}

		pUI->chat()->addInfoMessage("-> Teleported to: %.2f %.2f %.2f", vecPosition.x, vecPosition.y, vecPosition.z);
	}

	if (pNetGame && !strncmp(pScriptName, "CODEWAY", 7))
	{
		RakNet::BitStream bsSend;
		bsSend.Write(vecPosition.x);
		bsSend.Write(vecPosition.y);
		bsSend.Write(vecPosition.z);
		pNetGame->GetRakClient()->RPC(&RPC_MapMarker, &bsSend, HIGH_PRIORITY, RELIABLE, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
	}

	return CRadar_SetCoordBlip(BlpType, vecPosition, nColour, DispFlag, pScriptName);
}

DECL_HOOK(float, CRadar_LimitRadarPoint, sa::CVector2D &point)
{
	sa::CVector2D orig = point;
	float dist = CRadar_LimitRadarPoint(point);

	if (Settings::radarrect())
	{
		bool origOutsideCircle = (orig.x * orig.x + orig.y * orig.y) > 1.0001f;
		bool wasClampedByOrig = (point.x != orig.x || point.y != orig.y);
		if (origOutsideCircle && wasClampedByOrig)
		{
			point = orig;
			float maxAxis = std::max(std::fabs(point.x), std::fabs(point.y));
			if (maxAxis > 1.0f)
			{
				point.x /= maxAxis;
				point.y /= maxAxis;
			}
			return std::sqrt(point.x * point.x + point.y * point.y);
		}
	}

	return dist;
}

DECL_HOOK(void, CRadar_DrawRadarGangOverlay, bool bFullMap)
{
	if (pNetGame)
	{
		CGangZonePool *pGangZonePool = pNetGame->GetGangZonePool();
		if (pGangZonePool)
		{
			pGangZonePool->Draw(bFullMap);
		}
	}
}

#if VER_x32
DECL_HOOK(uint32_t, CHudColours_GetIntColour, uint32_t index)
{
	return TranslateColorCodeToRGBA(index);
}
#else
DECL_HOOK(uint32_t, CHudColours_GetIntColour, uintptr_t _this, uint32_t index)
{
	return TranslateColorCodeToRGBA(index);
}
#endif

DECL_HOOK(uint32_t, CAutoMobile_ProcessEntityCollision, sa::CVehicle *_this, sa::CVehicle *pCollidedVehicle, uintptr_t a3)
{
	if (!pNetGame || !pNetGame->m_netSet->disableVehicleCollisions ||
		!_this || !pCollidedVehicle ||
		pCollidedVehicle->m_nModelIndex < 400 || pCollidedVehicle->m_nModelIndex > 611 ||
		!_this->pDriver || !pCollidedVehicle->pDriver)
	{
		return CAutoMobile_ProcessEntityCollision(_this, pCollidedVehicle, a3);
	}

	return 0;
}

DECL_HOOK(uint32_t, CBike_ProcessEntityCollision, sa::CVehicle *_this, sa::CVehicle *pCollidedVehicle, uintptr_t a3)
{
	if (!pNetGame || !pNetGame->m_netSet->disableVehicleCollisions ||
		!_this || !pCollidedVehicle ||
		pCollidedVehicle->m_nModelIndex < 400 || pCollidedVehicle->m_nModelIndex > 611 ||
		!_this->pDriver || !pCollidedVehicle->pDriver)
	{
		return CBike_ProcessEntityCollision(_this, pCollidedVehicle, a3);
	}

	return 0;
}

DECL_HOOK(uint32_t, CMonsterTruck_ProcessEntityCollision, sa::CVehicle *_this, sa::CVehicle *pCollidedVehicle, uintptr_t a3)
{
	if (!pNetGame || !pNetGame->m_netSet->disableVehicleCollisions ||
		!_this || !pCollidedVehicle ||
		pCollidedVehicle->m_nModelIndex < 400 || pCollidedVehicle->m_nModelIndex > 611 ||
		!_this->pDriver || !pCollidedVehicle->pDriver)
	{
		return CMonsterTruck_ProcessEntityCollision(_this, pCollidedVehicle, a3);
	}

	return 0;
}

DECL_HOOK(uint32_t, CTrailer_ProcessEntityCollision, sa::CVehicle *_this, sa::CVehicle *pCollidedVehicle, uintptr_t a3)
{
	if (!pNetGame || !pNetGame->m_netSet->disableVehicleCollisions ||
		!_this || !pCollidedVehicle ||
		pCollidedVehicle->m_nModelIndex < 400 || pCollidedVehicle->m_nModelIndex > 611 ||
		!_this->pDriver || !pCollidedVehicle->pDriver)
	{
		if (!pCollidedVehicle->pDriver)
		{
			if (pNetGame)
			{
				CVehiclePool *pVehiclePool = pNetGame->GetVehiclePool();
				if (pVehiclePool)
				{
					VEHICLEID vehicleId = pVehiclePool->FindIDFromGtaPtr(pCollidedVehicle);
					if (vehicleId != INVALID_VEHICLE_ID)
					{
						CVehicle *pVehicle = pVehiclePool->GetAt(vehicleId);
						if (pVehicle)
						{
							// trailer has attached to any tractor
							if (pVehicle->GetTractor() != NULL)
							{
								return 0;
							}
						}
					}
				}
			}
		}

		return CTrailer_ProcessEntityCollision(_this, pCollidedVehicle, a3);
	}

	return 0;
}

// fix crosshair
float *m_f3rdPersonCHairMultX, *m_f3rdPersonCHairMultY;

DECL_HOOK(void, DrawCrosshair)
{
	float save1 = *m_f3rdPersonCHairMultX;
	*m_f3rdPersonCHairMultX = 0.52f;

	float save2 = *m_f3rdPersonCHairMultY;
	*m_f3rdPersonCHairMultY = 0.435f;

	DrawCrosshair();

	*m_f3rdPersonCHairMultX = save1;
	*m_f3rdPersonCHairMultY = save2;
}

void Hooks::installWidgetFixHooks()
{
	spdlog::info("Installing widget fix hooks...");

	g_pWidgets = *g_saSym->Abs<uintptr_t **>(VER_x32 ? 0x679474 : 0x850910);

	// HOOK("_ZN7CWidgetC2EPKcRK14WidgetPositionj10HIDMapping", CWidget);
	HOOK("_ZN7CWidget6UpdateEv", CWidget_Update);
	HOOK("_ZN7CWidget10SetEnabledEb", CWidget_SetEnabled);
}

void Hooks::installCrosshairFixHooks()
{
	spdlog::info("Installing crosshair fix hooks...");

	m_f3rdPersonCHairMultX = g_saSym->GetSymbol<float *>("_ZN7CCamera22m_f3rdPersonCHairMultXE");
	m_f3rdPersonCHairMultY = g_saSym->GetSymbol<float *>("_ZN7CCamera22m_f3rdPersonCHairMultYE");

	HOOK("_ZN4CHud14DrawCrossHairsEv", DrawCrosshair);
}

void Hooks::install()
{
	spdlog::info("Installing hooks...");

	msCollected = g_saSym->GetSymbol<unsigned int *>("msCollected");
	curMS = *g_saSym->GetSymbol<unsigned int *>("curMS");

	HOOK("_Z11OS_FileOpen14OSFileDataAreaPPvPKc16OSFileAccessType", OS_FileOpen);
	HOOK("_Z18OS_FileGetPositionPv", OS_FileGetPosition);
	HOOK("_Z11OS_FileReadPvS_i", OS_FileRead);
	HOOK("_Z18OS_FileSetPositionPvi", OS_FileSetPosition);
	HOOK("_Z11OS_FileSizePv", OS_FileSize);
#ifdef __aarch64__
	HOOK("_ZN11CFileLoader8LoadLineEy", CFileLoader_LoadLine);
	HOOK("_ZN8CFileMgr8ReadLineEyPci", CFileMgr_ReadLine);
#else
	HOOK("_ZN11CFileLoader8LoadLineEj", CFileLoader_LoadLine);
	HOOK("_ZN8CFileMgr8ReadLineEjPci", CFileMgr_ReadLine);
#endif
	HOOK("_ZN8CFileMgr8OpenFileEPKcS1_", CFileMgr_OpenFile);
#ifdef __aarch64__
	HOOK("_ZN8CFileMgr9CloseFileEy", CFileMgr_CloseFile);
#else
	HOOK("_ZN8CFileMgr9CloseFileEj", CFileMgr_CloseFile);
#endif
	HOOK("_ZN10CTimeCycle10InitialiseEb", CTimeCycle_Initialise);
	HOOK("_ZN14SurfaceInfos_c18LoadAdhesiveLimitsEv", SurfaceInfos_c_LoadAdhesiveLimits);
	HOOK("_ZN10CStreaming13InitImageListEv", CStreaming_InitImageList);
	HOOK("_ZN10CStreaming5Init2Ev", CStreaming_Init2);
	HOOK("_ZN6CTimer14StartUserPauseEv", CTimer_StartUserPause);
	HOOK("_ZN6CTimer12EndUserPauseEv", CTimer_EndUserPause);
	HOOK("_Z15ValidateVersionv", ValidateVersion);
	HOOK("_ZN5CGame20InitialiseRenderWareEv", CGame_InitialiseRenderWare);
	HOOK("_ZN5CGame27InitialiseEssentialsAfterRWEv", CGame_InitialiseEssentialsAfterRW);
	HOOK("_ZN9CPopCycle10InitialiseEv", CPopCycle_Initialise);
	HOOK("_ZN5CGame25InitialiseCoreDataAfterRWEv", CGame_InitialiseCoreDataAfterRW);
	HOOK("_ZN10CModelInfo11AddPedModelEi", CModelInfo_AddPedModel);
	HOOK("_ZN10CModelInfo14AddAtomicModelEi", CModelInfo_AddAtomicModel);
	HOOK("_ZN6CPools10InitialiseEv", CPools_Initialise);
	HOOK("_ZN9CTxdStore14TxdStoreFindCBEPKc", CTxdStore_TxdStoreFindCB);

	// SAMP hooks //

	// UI
	HOOK("_Z12ANDRunThreadPv", AND_RunThread);
	HOOK("_Z13Render2dStuffv", Render2dStuff);
	HOOK("_Z4IdlePvb", Idle);
	HOOK("_Z14AND_TouchEventiiii", AND_TouchEvent);
	HOOK("_ZN14CLoadingScreen15DisplayPCScreenEv", DisplayScreen);

	// Game process
	HOOK("_ZN5CGame7ProcessEv", CGame_Process);

	// click map rpc
	HOOK("_ZN6CRadar12SetCoordBlipE9eBlipType7CVectorj12eBlipDisplayPc", CRadar_SetCoordBlip);

	// keep radar blips clamped cleanly (circular or radar_rect)
	HOOK("_ZN6CRadar15LimitRadarPointER9CVector2D", CRadar_LimitRadarPoint);

	// gangzone
	HOOK("_ZN6CRadar20DrawRadarGangOverlayEb", CRadar_DrawRadarGangOverlay);

	// radar color
	HOOK("_ZN11CHudColours12GetIntColourEh", CHudColours_GetIntColour);

	// vehicle collisions
	HOOK("_ZN11CAutomobile22ProcessEntityCollisionEP7CEntityP9CColPoint", CAutoMobile_ProcessEntityCollision);
	HOOK("_ZN5CBike22ProcessEntityCollisionEP7CEntityP9CColPoint", CBike_ProcessEntityCollision);
	HOOK("_ZN13CMonsterTruck22ProcessEntityCollisionEP7CEntityP9CColPoint", CMonsterTruck_ProcessEntityCollision);
	HOOK("_ZN8CTrailer22ProcessEntityCollisionEP7CEntityP9CColPoint", CTrailer_ProcessEntityCollision);

	// obj material
	HOOK("_ZN7CObject6RenderEv", CObject_Render);

	// render objects 3000+- pos
	HOOK("_ZN9CRenderer24RenderEverythingBarRoadsEv", CRenderer_RenderEverythingBarRoads);

	// attched objects
	HOOK("_ZN6CWorld25ProcessPedsAfterPreRenderEv", CWorld_ProcessPedsAfterPreRender);

	// crash fixes
	HOOK("_ZN6CRadar9ClearBlipEi", CRadar_ClearBlip);
	HOOK("_ZN6CWorld28FindPlayerSlotWithPedPointerEPv", CWorld_FindPlayerSlotWithPedPointer);
	HOOK("_ZN25CCustomBuildingDNPipeline18CustomPipeRenderCBEP10RwResEntryPvhj", CustomPipeRenderCB);
	HOOK("_Z32_rxOpenGLDefaultAllInOneRenderCBP10RwResEntryPvhj", rxOpenGLDefaultAllInOneRenderCB);
	HOOK("_Z16RwTextureDestroyP9RwTexture", _RwTextureDestroy);
	HOOK("_Z15RwFrameAddChildP7RwFrameS0_", RwFrameAddChild);
	HOOK("_Z13RLEDecompressPhjPKhjj", RLEDecompress);
	HOOK("_Z23RwResourcesFreeResEntryP10RwResEntry", RwResourcesFreeResEntry);
	HOOK("_ZN22TextureDatabaseRuntime4LoadEPKcb21TextureDatabaseFormat", TextureDatabaseRuntime_Load);
	HOOK("_ZN22TextureDatabaseRuntime8GetEntryEPKcRb", TextureDatabaseRuntime_GetEntry);
	HOOK("_ZN22TextureDatabaseRuntime10GetTextureEPKc", TextureDatabaseRuntime_GetTexture);
	HOOK("_Z32RpAnimBlendClumpUpdateAnimationsP7RpClumpfb", RpAnimBlendClumpUpdateAnimations);
	HOOK("_ZN19CAnimBlendClumpData12ForAllFramesEPFvP18AnimBlendFrameDataPvES2_", CAnimBlendClumpData_ForAllFrames);
	HOOK("_ZN10MobileMenu12InitForTitleEv", MobileMenu_InitForTitle);
	HOOK("_ZN10MobileMenu4LoadEv", MobileMenu_Load);
	HOOK("_ZN10MobileMenu12InitForPauseEv", MobileMenu_InitForPause);
	HOOK("_Z10OALServicev", OALService);
	HOOK("_ZN22CAEFrontendAudioEntity13AddAudioEventEiff", CAEFrontendAudioEntity_AddAudioEvent);
	HOOK("_ZN10CPlayerPed29GetPlayerInfoForThisPlayerPedEv", GetPlayerInfoForThisPlayerPed);

	if (!eglGetProcAddress("glAlphaFuncQCOM"))
	{
		// If "glAlphaFuncQCOM" is not available, try "glAlphaFunc"
		if (eglGetProcAddress("glAlphaFunc"))
		{
			// If "glAlphaFunc" is found, store the address in the global library
			*((void **)(g_saSym->Abs(VER_x32 ? 0x6BCBF8 : 0x89A1B0))) = (void *)eglGetProcAddress("glAlphaFunc");
		}
		else
		{
			// If neither function is available, hook the fallback symbol
			HOOK("_Z25RQ_Command_rqSetAlphaTestRPc", RQCommand_rqSetAlphaTest);
		}
	}

	// fix spin on keyboard/controller
	HOOK("_ZN4CHID12GetInputTypeEv", GetInputType);

	Pickups::installHooks();
	RemoveBuilding::installHooks();

	installPadHooks();
	installWidgetFixHooks();
	installCrosshairFixHooks();
}
