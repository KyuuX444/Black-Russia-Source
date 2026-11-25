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
	/*
	 * using the loadingscreen from java so we don't need this anymore
	 */

	/*if (RwCameraBeginUpdate(sa::Scene->m_pRwCamera)) {
		// DefinedState2d
		Memory::callFunction<void>("_Z14DefinedState2dv");

		// CSprite2d::InitPerFrame
		Memory::callFunction<void>("_ZN9CSprite2d12InitPerFrameEv");

		RwRenderStateSet(rwRENDERSTATETEXTUREADDRESS, (void*) rwTEXTUREADDRESSCLAMP);

		// emu_GammaSet
		Memory::callFunction("_Z12emu_GammaSeth", false);

		// CLoadingScreen::m_PercentLoaded
		float fPercentLoaded = *g_saSym->GetSymbol<float*>("_ZN14CLoadingScreen15m_PercentLoadedE");

		pUI->splashscreen()->setProgressBarValue(fPercentLoaded / 100.0f);
		pUI->render();

		if (sa::Scene && sa::Scene->m_pRwCamera)
	{
		RwCameraEndUpdate(sa::Scene->m_pRwCamera);
	}
		RwCameraShowRaster(sa::Scene->m_pRwCamera, nullptr, 0);
	}*/
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
	return access(path.c_str(), R_OK) == 0;
}

static std::mutex s_fsCacheMutex;
static std::vector<std::string> s_validBases;
static bool s_basesInitialized = false;
static std::unordered_map<std::string, std::unordered_map<std::string, std::string>> s_dirCache;
static std::unordered_map<std::string, std::string> s_pathCache;

static std::string to_lower_ascii(std::string s)
{
	for (char& c : s) {
		c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
	}
	return s;
}

static const std::vector<std::string>& get_valid_bases()
{
	if (s_basesInitialized) {
		return s_validBases;
	}
	const char* candidateBases[] = {
		Client::gameDir(),
		"/storage/emulated/0/ryznSAMP/",
		"/storage/emulated/0/Android/data/com.ryzn.samp/files/",
		"/data/data/com.ryzn.samp/files/",
		"/storage/emulated/0/Android/data/com.rockstargames.gtasa/files/"
	};
	for (const char* b : candidateBases) {
		if (!b || !b[0]) continue;
		std::string base(b);
		if (base.back() != '/') base += '/';
		bool dup = false;
		for (const auto& existing : s_validBases) {
			if (existing == base) {
				dup = true;
				break;
			}
		}
		if (dup) continue;
		struct stat st{};
		if (stat(base.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) {
			s_validBases.push_back(std::move(base));
		}
	}
	s_basesInitialized = true;
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

static std::string find_real_file(const std::string& requestedPath, bool useCache = true)
{
	std::string clean = requestedPath;
	for (char& c : clean) {
		if (c == '\\') c = '/';
	}

	// Extract relative part if requestedPath contains game directory or /files/
	std::string rel = clean;
	const char* pFiles = strstr(clean.c_str(), "/files/");
	if (pFiles) {
		rel = pFiles + 7;
	} else if (!clean.empty() && clean.front() == '/') {
		if (clean.find(Client::gameDir()) == 0) {
			rel = clean.substr(strlen(Client::gameDir()));
		}
	}
	while (!rel.empty() && rel.front() == '/') {
		rel.erase(rel.begin());
	}
	if (rel.empty()) {
		return "";
	}

	std::string cacheKey = to_lower_ascii(rel);
	std::lock_guard<std::mutex> lock(s_fsCacheMutex);

	if (useCache) {
		auto cached = s_pathCache.find(cacheKey);
		if (cached != s_pathCache.end()) {
			return cached->second;
		}
	}

	std::string foundPath;
	for (const auto& base : get_valid_bases()) {
		std::string resolved = resolve_case_insensitive_path(base, rel);
		if (!resolved.empty()) {
			foundPath = std::move(resolved);
			break;
		}

		// Special case: decision files (PedEvent.txt, *.ped, *.grp) might be inside data/Decision/
		if (rel.find("Decision") == std::string::npos && (rel.find(".ped") != std::string::npos || rel.find(".grp") != std::string::npos || rel == "PedEvent.txt")) {
			std::string tryDec1 = resolve_case_insensitive_path(base, "data/Decision/" + rel);
			if (!tryDec1.empty()) {
				foundPath = std::move(tryDec1);
				break;
			}
			std::string tryDec2 = resolve_case_insensitive_path(base, "data/Decision/Allowed/" + rel);
			if (!tryDec2.empty()) {
				foundPath = std::move(tryDec2);
				break;
			}
		}

		// Bare config/data filenames requested without directory prefix (e.g. TouchDefaultPhoneWidescreen.cfg, ShopUVs.dat)
		if (rel.find('/') == std::string::npos) {
			std::string tryData = resolve_case_insensitive_path(base, "data/" + rel);
			if (!tryData.empty()) {
				foundPath = std::move(tryData);
				break;
			}
		}
	}

	if (useCache) {
		s_pathCache.emplace(std::move(cacheKey), foundPath);
	}
	return foundPath;
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
		if (!s_retrying && filename[0] != '/') {
			std::string real = find_real_file(filename, isReadOnly);
			if (!real.empty()) {
				std::string rel = to_storage_relative(real);
				if (!rel.empty() && rel != filename) {
					s_retrying = true;
					handle = CFileMgr_OpenFile(rel.c_str(), mode);
					s_retrying = false;
					if (handle) {
						spdlog::debug("CFileMgr::OpenFile(\"{}\") resolved -> \"{}\"", filename, rel);
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

	std::string extSamp = std::string(Client::gameDir()) + "SAMP/surface.dat";
	std::string extData = std::string(Client::gameDir()) + "data/surface.dat";
	if (check_file_readable(extSamp) || check_file_readable(extData)) {
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

DECL_HOOK(int, OS_FileOpen, int a1, uintptr_t handle, char *name, int a2)
{
	char path[512] = {0};

	if (!name) goto ret;

	if (!strncasecmp(name, "data\\surface.dat", 16) || !strncasecmp(name, "data/surface.dat", 16))
	{
		spdlog::info("OS_FileOpen: surface.dat requested");
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/surface.dat";
		std::string extData = std::string(Client::gameDir()) + "data/surface.dat";
		const char* intSamp = "/data/data/com.ryzn.samp/files/SAMP/surface.dat";
		const char* intData = "/data/data/com.ryzn.samp/files/data/surface.dat";

		if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\surface.dat");
			name = path;
		} else if (check_file_readable(extData)) {
			sprintf(path, "data\\surface.dat");
			name = path;
		} else if (check_file_readable(intSamp)) {
			sprintf(path, "%s", intSamp);
			name = path;
		} else if (check_file_readable(intData)) {
			sprintf(path, "%s", intData);
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\surfinfo.dat", 17) || !strncasecmp(name, "data/surfinfo.dat", 17))
	{
		spdlog::info("OS_FileOpen: surfinfo.dat requested");
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/surfinfo.dat";
		std::string extData = std::string(Client::gameDir()) + "data/surfinfo.dat";
		const char* intSamp = "/data/data/com.ryzn.samp/files/SAMP/surfinfo.dat";

		if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\surfinfo.dat");
			name = path;
		} else if (check_file_readable(extData)) {
			sprintf(path, "data\\surfinfo.dat");
			name = path;
		} else if (check_file_readable(intSamp)) {
			sprintf(path, "%s", intSamp);
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\surfaud.dat", 16) || !strncasecmp(name, "data/surfaud.dat", 16))
	{
		spdlog::info("OS_FileOpen: surfaud.dat requested");
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/surfaud.dat";
		std::string extData = std::string(Client::gameDir()) + "data/surfaud.dat";
		const char* intSamp = "/data/data/com.ryzn.samp/files/SAMP/surfaud.dat";

		if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\surfaud.dat");
			name = path;
		} else if (check_file_readable(extData)) {
			sprintf(path, "data\\surfaud.dat");
			name = path;
		} else if (check_file_readable(intSamp)) {
			sprintf(path, "%s", intSamp);
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\script\\mainV1.scm", 22) || !strncasecmp(name, "data/script/mainV1.scm", 22))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/main.scm";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading mainV1.scm -> SAMP\\main.scm..");
			sprintf(path, "SAMP\\main.scm");
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "DATA\\SCRIPT\\SCRIPTV1.IMG", 24) || !strncasecmp(name, "data/script/scriptv1.img", 24))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/script.img";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading scriptV1.img -> SAMP\\script.img..");
			sprintf(path, "SAMP\\script.img");
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "DATA\\GTA.DAT", 12) || !strncasecmp(name, "data/gta.dat", 12))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/gta.dat";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading gta.dat -> SAMP\\gta.dat..");
			sprintf(path, "SAMP\\gta.dat");
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "DATA\\PEDS.IDE", 13) || !strncasecmp(name, "data/peds.ide", 13))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/peds.ide";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading peds.ide -> SAMP\\peds.ide..");
			sprintf(path, "SAMP\\peds.ide");
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "DATA\\TIMECYC.DAT", 16) || !strncasecmp(name, "data/timecyc.dat", 16) || !strcasecmp(name, "TIMECYC.DAT"))
	{
		spdlog::info("OS_FileOpen: timecyc.dat requested ({})", name);
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/timecyc.dat";
		std::string extSampUpper = std::string(Client::gameDir()) + "SAMP/TIMECYC.DAT";
		std::string extData = std::string(Client::gameDir()) + "data/timecyc.dat";
		std::string extDataUpper = std::string(Client::gameDir()) + "data/TIMECYC.DAT";

		if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\timecyc.dat");
			name = path;
			spdlog::info("Loading modpack timecyc.dat from SAMP\\timecyc.dat");
		} else if (check_file_readable(extSampUpper)) {
			sprintf(path, "SAMP\\TIMECYC.DAT");
			name = path;
			spdlog::info("Loading modpack timecyc.dat from SAMP\\TIMECYC.DAT");
		} else if (check_file_readable(extData)) {
			sprintf(path, "data\\timecyc.dat");
			name = path;
			spdlog::info("Loading modpack timecyc.dat from data\\timecyc.dat");
		} else if (check_file_readable(extDataUpper)) {
			sprintf(path, "data\\TIMECYC.DAT");
			name = path;
			spdlog::info("Loading modpack timecyc.dat from data\\TIMECYC.DAT");
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\colorcycle.dat", 19) || !strncasecmp(name, "data/colorcycle.dat", 19) || !strcasecmp(name, "colorcycle.dat"))
	{
		spdlog::info("OS_FileOpen: colorcycle.dat requested ({})", name);
		std::string extData = std::string(Client::gameDir()) + "data/colorcycle.dat";
		std::string extDataUpper = std::string(Client::gameDir()) + "data/COLORCYCLE.DAT";
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/colorcycle.dat";
		std::string extSampUpper = std::string(Client::gameDir()) + "SAMP/COLORCYCLE.DAT";

		if (check_file_readable(extData)) {
			sprintf(path, "data\\colorcycle.dat");
			name = path;
			spdlog::info("Loading modpack colorcycle.dat from data\\colorcycle.dat");
		} else if (check_file_readable(extDataUpper)) {
			sprintf(path, "data\\COLORCYCLE.DAT");
			name = path;
			spdlog::info("Loading modpack colorcycle.dat from data\\COLORCYCLE.DAT");
		} else if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\colorcycle.dat");
			name = path;
			spdlog::info("Loading modpack colorcycle.dat from SAMP\\colorcycle.dat");
		} else if (check_file_readable(extSampUpper)) {
			sprintf(path, "SAMP\\COLORCYCLE.DAT");
			name = path;
			spdlog::info("Loading modpack colorcycle.dat from SAMP\\COLORCYCLE.DAT");
		}
		goto ret;
	}

	if (!strncasecmp(name, "DATA\\POPCYCLE.DAT", 17) || !strncasecmp(name, "data/popcycle.dat", 17) || !strcasecmp(name, "POPCYCLE.DAT"))
	{
		spdlog::info("OS_FileOpen: popcycle.dat requested ({})", name);
		std::string extData = std::string(Client::gameDir()) + "data/popcycle.dat";
		std::string extDataUpper = std::string(Client::gameDir()) + "data/POPCYCLE.DAT";
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/popcycle.dat";
		std::string extSampUpper = std::string(Client::gameDir()) + "SAMP/POPCYCLE.DAT";

		if (check_file_readable(extData)) {
			sprintf(path, "data\\popcycle.dat");
			name = path;
			spdlog::info("Loading popcycle.dat from data\\popcycle.dat");
		} else if (check_file_readable(extDataUpper)) {
			sprintf(path, "data\\POPCYCLE.DAT");
			name = path;
			spdlog::info("Loading popcycle.dat from data\\POPCYCLE.DAT");
		} else if (check_file_readable(extSamp)) {
			sprintf(path, "SAMP\\popcycle.dat");
			name = path;
			spdlog::info("Loading popcycle.dat from SAMP\\popcycle.dat");
		} else if (check_file_readable(extSampUpper)) {
			sprintf(path, "SAMP\\POPCYCLE.DAT");
			name = path;
			spdlog::info("Loading popcycle.dat from SAMP\\POPCYCLE.DAT");
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\paths\\tracks2.dat", 22) || !strncasecmp(name, "data/paths/tracks2.dat", 22))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/tracks2.dat";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading tracks2.dat -> SAMP/tracks2.dat...");
			snprintf(path, sizeof(path), "SAMP/tracks2.dat");
			name = path;
		}
		goto ret;
	}

	if (!strncasecmp(name, "data\\paths\\tracks4.dat", 22) || !strncasecmp(name, "data/paths/tracks4.dat", 22))
	{
		std::string extSamp = std::string(Client::gameDir()) + "SAMP/tracks4.dat";
		if (check_file_readable(extSamp)) {
			spdlog::info("Loading tracks4.dat -> SAMP/tracks4.dat...");
			snprintf(path, sizeof(path), "SAMP/tracks4.dat");
			name = path;
		}
		goto ret;
	}

ret:
	char normBuf[512];
	if (name) {
		char* storageRoot = g_saSym ? g_saSym->GetSymbol<char*>("StorageRootBuffer") : nullptr;
		if (storageRoot && Client::gameDir() && Client::gameDir()[0] && strcmp(storageRoot, Client::gameDir()) != 0) {
			snprintf(storageRoot, 512, "%s", Client::gameDir());
		}

		std::string real = find_real_file(name, a2 != 1 && a2 != 2);
		std::string relName = !real.empty() ? to_storage_relative(real) : "";
		if (!relName.empty()) {
			snprintf(path, sizeof(path), "%s", relName.c_str());
			name = path;
		} else {
			strncpy(normBuf, name, sizeof(normBuf) - 1);
			normBuf[sizeof(normBuf) - 1] = '\0';
			bool modified = false;
			for (char* p = normBuf; *p; ++p) {
				if (*p == '\\') { *p = '/'; modified = true; }
			}
			if (modified) {
				name = normBuf;
			}
		}
	}
	return OS_FileOpen(a1, handle, name, a2);
}

DECL_HOOK(sa::CPedModelInfo *, CModelInfo_AddPedModel, int id)
{
	// spdlog::info("Adding ped model: {}", id);
	auto model = new sa::CPedModelInfo{};
	model->vtable = g_saSym->GetVmtFunction<uintptr_t>("_ZTV13CPedModelInfo", 1, true); // CPedModelInfo::~CPedModelInfo

	typedef sa::CStore<sa::CPedModelInfo, 278> PedModelStore;
	auto ms_pedModelStore = g_saSym->Abs<PedModelStore *>(addr::ms_pedModelStore);
	ms_pedModelStore->AddItem();

	// CClumpModelInfo::Init
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

DECL_HOOK(RwTexture *, CTxdStore_TxdStoreFindCB, const char *texture_name)
{
	// spdlog::info("Finding texture: {}", texture_name);

	std::vector<std::string> texture_databases = {"gta_int", "gta3", "samp", "menu", "mobile"};
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

		Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", "TEXDB\\SAMPCOL.IMG", true);
		Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", "TEXDB\\GTA3.IMG", true);
		Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", "TEXDB\\GTA_INT.IMG", true);
		Memory::callFunction<void>("_ZN10CStreaming14AddImageToListEPKcb", "TEXDB\\SAMP.IMG", true);
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
		// fps
		msCollected[curMS++ % 0x28] = Memory::callFunction<unsigned int>("_Z9OS_TimeMSv");

		RGBA bgColor = {0, 0, 0, 0};
		RGBA color = {255, 255, 255, 255};

		CFont::SetBackgroundColor(&bgColor);
		CFont::SetScale(1.12f * 0.54, 1.12f);
		CFont::SetOrientation(1);
		CFont::SetJustify(0);
		CFont::SetWrapX(640.0);
		CFont::SetProportional(0);
		CFont::SetFontStyle(1);
		CFont::SetEdge(0);
		CFont::SetColor(&color);

		char szStr[64];
		sprintf(szStr, "FPS: %.2f", (float)(39000.0 / (float)(int)(msCollected[(curMS - 1) % 0x28] - msCollected[curMS % 0x28])));
		CFont::PrintString((float)((float)sa::RsGlobal->screenWidth / 640.0) * 4.0, (float)((float)sa::RsGlobal->screenHeight / 448.0) * 200.0, szStr);

		// mem
		CFont::SetBackgroundColor(&bgColor);
		CFont::SetScale(1.12f * 0.54, 1.12f);
		CFont::SetOrientation(1);
		CFont::SetJustify(0);
		CFont::SetWrapX(640.0);
		CFont::SetProportional(0);
		CFont::SetFontStyle(1);
		CFont::SetEdge(0);
		CFont::SetColor(&color);

		struct mallinfo2 memInfo = mallinfo2();

		long long totalAllocatedMB = memInfo.uordblks / (1024 * 1024);

#if VER_x32
		long long totalMemoryMB = 0;
		FILE *meminfo = fopen("/proc/meminfo", "r");
		if (meminfo)
		{
			char line[256];
			while (fgets(line, sizeof(line), meminfo))
			{
				if (strncmp(line, "MemTotal:", 9) == 0)
				{
					totalMemoryMB = atoll(&line[9]) / 1024;
					break;
				}
			}
			fclose(meminfo);
		}
#else
		struct sysinfo info{};
		long long totalMemoryMB = 0;
		if (sysinfo(&info) == 0)
		{
			totalMemoryMB = info.totalram / (1024 * 1024); // Get total system RAM
		}
#endif

		char szStrMem[64];
		snprintf(szStrMem, sizeof(szStrMem), "MEM: %lld / %lld MB", totalAllocatedMB, totalMemoryMB);
		CFont::PrintString((float)((float)sa::RsGlobal->screenWidth / 640.0) * 4.0, (float)((float)sa::RsGlobal->screenHeight / 448.0) * 210.0, szStrMem);

		// arch
		CFont::SetBackgroundColor(&bgColor);
		CFont::SetScale(1.12f * 0.54, 1.12f);
		CFont::SetOrientation(1);
		CFont::SetJustify(0);
		CFont::SetWrapX(640.0);
		CFont::SetProportional(0);
		CFont::SetFontStyle(1);
		CFont::SetEdge(0);
		CFont::SetColor(&color);

		char szStrArch[64];
		snprintf(szStrArch, sizeof(szStrArch), "ARCH: %s", VER_x32 ? "x32" : "x64");
		CFont::PrintString((float)((float)sa::RsGlobal->screenWidth / 640.0) * 4.0, (float)((float)sa::RsGlobal->screenHeight / 448.0) * 220.0, szStrArch);
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
	for (int i = 0; i < MAX_PLAYERS; i++)
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
	for (int i = 0; i < MAX_PLAYERS; ++i)
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
	HOOK("_ZN22TextureDatabaseRuntime8GetEntryEPKcRb", TextureDatabaseRuntime_GetEntry);
	HOOK("_ZN22TextureDatabaseRuntime10GetTextureEPKc", TextureDatabaseRuntime_GetTexture);
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
