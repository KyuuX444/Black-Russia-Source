#include "Client.h"
#include "BackTrace.h"
#include "Settings.h"
#include "ClientCmds.h"
#include "Game/Patches.h"
#include "Game/Hooks.h"
#include "Java/Java.h"
#include "Java/MultiTouch.h"
#include "UI/UI.h"
#include "Game/Game.h"
#include "Game/AudioStream.h"
#include "Net/NetGame.h"
#include "Voice/Plugin.h"
#include "DefaultDataFiles.h"
#include <sys/stat.h>
#include <sys/types.h>

JavaVM* Client::java_vm = nullptr;
std::string Client::game_dir;
bool Client::offline_mode = false;
std::string Client::samp_version;

Java* g_java = nullptr;
UI* pUI = nullptr;
Game* pGame = nullptr;
NetGame* pNetGame = nullptr;
PlayerTags* pPlayerTags = nullptr;
AudioStream* pAudioStream = nullptr;
SnapShotHelper* pSnapShotHelper = nullptr;
MaterialTextGenerator* pMaterialTextGenerator = nullptr;

SymUtils* g_saSym = new SymUtils();
JavaVM* g_vm = nullptr;

static bool mkdir_p(const std::string& path)
{
	size_t pos = 0;
	while ((pos = path.find_first_of('/', pos + 1)) != std::string::npos) {
		std::string sub = path.substr(0, pos);
		if (!sub.empty()) {
			mkdir(sub.c_str(), 0777);
		}
	}
	return mkdir(path.c_str(), 0777) == 0 || errno == EEXIST;
}

void init_logger(const std::string& gameDir)
{
	try {
		mkdir_p(gameDir + "logcat/");

		auto now = std::chrono::system_clock::now();
		auto in_time_t = std::chrono::system_clock::to_time_t(now);
		std::stringstream ss;
		ss << std::put_time(std::localtime(&in_time_t), "%Y-%m-%d_%H-%M-%S");
		std::string dateTime = ss.str();

		// Define log file path client+date+time.log
		std::string path = gameDir + "logcat/client_" + dateTime + ".log";

		// Create Android (logcat) sink
		auto android_sink = std::make_shared<spdlog::sinks::android_sink_mt>("Ryzn SA-MP");

		// Create file sink
		auto max_size = 1048576 * 10; // Max file size 10MB
		auto max_files = 3; // Rotate files
		auto file_sink = std::make_shared<spdlog::sinks::rotating_file_sink_mt>(path, max_size, max_files);

		// Combine both sinks
		std::vector<spdlog::sink_ptr> sinks{android_sink, file_sink};
		auto logger = std::make_shared<spdlog::logger>("Ryzn SA-MP", sinks.begin(), sinks.end());

		// Set log level and pattern
		logger->set_level(spdlog::level::info);
		logger->set_pattern("[%d-%m-%Y %H:%M:%S] [%^%l%$] %v");
		logger->flush_on(spdlog::level::info);

		// Set as default logger
		spdlog::set_default_logger(logger);
		spdlog::flush_every(std::chrono::seconds(1));
	}
	catch (const spdlog::spdlog_ex& ex) {
		__android_log_print(ANDROID_LOG_ERROR, "Ryzn SA-MP", "Log initialization failed: %s", ex.what());
	}
}

void* startGame(void*)
{
	spdlog::info("startGame thread started, waiting for gGameState >= 7...");
	uint32_t* pGameState = g_saSym->GetSymbol<uint32_t*>("gGameState");
	uint32_t lastLogged = 999;
	int ticks = 0;
	while (true) {
		uint32_t state = pGameState ? *pGameState : 0;
		if (state != lastLogged) {
			spdlog::info("gGameState transitioned: {} -> {}", lastLogged, state);
			lastLogged = state;
		}
		if (state >= 7) {
			break;
		}
		if (++ticks % 2000 == 0) { // every ~1s (2000 * 500us)
			spdlog::info("Waiting for gGameState >= 7 (current={})...", state);
		}
		usleep(500);
	}

	spdlog::info("gGameState is {} (>= 7), calling StartGame()...", lastLogged);
	pGame->StartGame();
	spdlog::info("pGame->StartGame() returned");

	pthread_exit(nullptr);
}

static void ensure_surface_dat(const std::string& gameDir)
{
	static const char* default_surface_dat =
		"; Surface.dat\n"
		"; Rubber  Hard  Road  Loose  Sand  Wet\n"
		"Rubber   6.0\n"
		"Hard     3.6   2.0\n"
		"Road     4.5   3.0   6.0\n"
		"Loose    3.2   3.5   2.0   1.0\n"
		"Sand     2.0   4.0   2.0   1.0   1.0\n"
		"Wet      2.4   2.0   1.0   1.0   1.0   0.5\n";

	std::string targetPaths[] = {
		gameDir + "SAMP/surface.dat",
		gameDir + "data/surface.dat",
		"/data/data/com.ryzn.samp/files/SAMP/surface.dat",
		"/data/data/com.ryzn.samp/files/data/surface.dat"
	};

	for (const auto& target : targetPaths) {
		FILE* check = fopen(target.c_str(), "rb");
		if (check) {
			fclose(check);
			continue;
		}
		size_t lastSlash = target.find_last_of('/');
		if (lastSlash != std::string::npos) {
			std::string dir = target.substr(0, lastSlash);
			mkdir_p(dir);
		}
		FILE* out = fopen(target.c_str(), "wb");
		if (out) {
			fwrite(default_surface_dat, 1, strlen(default_surface_dat), out);
			fclose(out);
			spdlog::info("Created default surface.dat at: {}", target);
		}
	}
}

static void ensure_data_files(const std::string& gameDir)
{
	ensure_surface_dat(gameDir);

	// Ensure timecyc.dat
	std::string timecycTargets[] = {
		gameDir + "SAMP/timecyc.dat",
		gameDir + "data/timecyc.dat",
		"/data/data/com.ryzn.samp/files/SAMP/timecyc.dat",
		"/data/data/com.ryzn.samp/files/data/timecyc.dat"
	};
	for (const auto& target : timecycTargets) {
		FILE* check = fopen(target.c_str(), "rb");
		if (check) {
			fclose(check);
			continue;
		}
		size_t lastSlash = target.find_last_of('/');
		if (lastSlash != std::string::npos) {
			std::string dir = target.substr(0, lastSlash);
			mkdir_p(dir);
		}
		FILE* out = fopen(target.c_str(), "wb");
		if (out) {
			fwrite(default_timecyc_dat, 1, default_timecyc_dat_len, out);
			fclose(out);
			spdlog::info("Created default timecyc.dat at: {}", target);
		}
	}

	// Ensure colorcycle.dat
	std::string colorcycleTargets[] = {
		gameDir + "data/colorcycle.dat",
		gameDir + "SAMP/colorcycle.dat",
		"/data/data/com.ryzn.samp/files/data/colorcycle.dat",
		"/data/data/com.ryzn.samp/files/SAMP/colorcycle.dat"
	};
	for (const auto& target : colorcycleTargets) {
		FILE* check = fopen(target.c_str(), "rb");
		if (check) {
			fclose(check);
			continue;
		}
		size_t lastSlash = target.find_last_of('/');
		if (lastSlash != std::string::npos) {
			std::string dir = target.substr(0, lastSlash);
			mkdir_p(dir);
		}
		FILE* out = fopen(target.c_str(), "wb");
		if (out) {
			fwrite(default_colorcycle_dat, 1, default_colorcycle_dat_len, out);
			fclose(out);
			spdlog::info("Created default colorcycle.dat at: {}", target);
		}
	}
}

void Client::initialize(const std::string& gameDir, bool offlineMode)
{
	// signal handler
	struct sigaction sig_action{};
	sig_action.sa_sigaction = [](int signal, siginfo_t* info, void* ctx) {
		dump_register(signal, info, ctx);
		dump_stack(2);
		exit(signal);
	};
	sigemptyset(&sig_action.sa_mask);
	sig_action.sa_flags = SA_SIGINFO;
	sigaction(SIGSEGV, &sig_action, nullptr);

	std::string resolvedGameDir = gameDir;
	struct stat st;
	std::string texdbCheck = resolvedGameDir + "texdb";
	if (stat(texdbCheck.c_str(), &st) != 0) {
		const char* candidatePaths[] = {
			"/storage/emulated/0/ryznSAMP/",
			"/storage/emulated/0/Android/data/com.ryzn.samp/files/",
			"/data/data/com.ryzn.samp/files/",
			"/storage/emulated/0/Android/data/com.rockstargames.gtasa/files/"
		};
		for (const char* cand : candidatePaths) {
			std::string candTexdb = std::string(cand) + "texdb";
			if (stat(candTexdb.c_str(), &st) == 0) {
				resolvedGameDir = cand;
				break;
			}
		}
	}

	init_logger(resolvedGameDir);
	ensure_data_files(resolvedGameDir);

	spdlog::info("Initializing Ryzn SA-MP client...");

	java_vm = g_vm;
	game_dir = resolvedGameDir;
	offline_mode = false; // Always force online multiplayer mode
	samp_version = "0.3.7";

	spdlog::info("Java VM: 0x{:X}", reinterpret_cast<uintptr_t>(java_vm));
	spdlog::info("Game version: {}", VER_x32 ? "GTA: SA v2.10 (armeabi-v7a)" : "GTA: SA v2.10 (arm64-v8a)");
	spdlog::info("Game directory: {}", game_dir);
	spdlog::info("Offline mode: {}", offline_mode);

	Settings::initialize();

	if (Settings::sampversion() == 0) {
		samp_version = "0.3.7";
	}
	else {
		samp_version = "0.3.7-R4";
	}

	spdlog::info("SA-MP version: {}", samp_version);

	spdlog::info("Loading libGTASA.so...");
	g_saSym->Open("libGTASA.so");

	if (!g_saSym->GetLibAddr()) {
		spdlog::error("Failed to load libGTASA.so!");
		return;
	}

	spdlog::info("libGTASA.so loaded at 0x{:X}", g_saSym->GetLibAddr());

	sa::Initialize();
	LoadBassLibrary();

	Hooks::install();
	Patches::apply();

	MultiTouch::initialize();

	pGame = new Game();

	if (Settings::voice()) {
		Plugin::OnPluginLoad();
		Plugin::OnSampLoad();
	}

	pthread_t thread;
	pthread_create(&thread, nullptr, startGame, nullptr);

	// FIXME: causes crash on some devices - why?
	/*std::thread([]() {
		while (*g_saSym->GetSymbol<uint32_t*>("gGameState") != 7) {
			std::this_thread::sleep_for(std::chrono::milliseconds(500));
		}

		pGame->StartGame();
	}).detach();*/
}

void Client::process()
{
	static bool gameInited = false;
	static bool netgameInited = false;

	if (!gameInited) {
		spdlog::info("Initializing game...");

		pPlayerTags = new PlayerTags();
		pAudioStream = new AudioStream();
		pSnapShotHelper = new SnapShotHelper();
		pMaterialTextGenerator = new MaterialTextGenerator();

		Patches::preApply();

		pGame->Initialize();
		pAudioStream->Initialize();

		if (pUI) {
			pUI->splashscreen()->setVisible(false);
			pUI->chat()->setVisible(true);
			pUI->buttonpanel()->setVisible(true);
		}

		if (g_java) {
			g_java->showLoadingScreen(false);
		}

		ClientCmds::initialize();

		if (Settings::voice()) {
			LogVoice("[dbg:samp:load] : module loading...");
			for (const auto& loadCallback : Samp::loadCallbacks) {
				if (loadCallback != nullptr) {
					loadCallback();
				}
			}
			Samp::loadStatus = true;
			LogVoice("[dbg:samp:load] : module loaded");
		}

		if (offline_mode) {
			pUI->buttonpanel()->setVisible(false);
			pUI->chat()->addInfoMessage("-> GTA: SA " + (VER_x32 ? std::string("v2.10 (armeabi-v7a)") : std::string("v2.10 (arm64-v8a)")) + " started");
			pUI->chat()->addInfoMessage("-> Type /j to start jetpack");
			pUI->chat()->addInfoMessage("-> Click on map to teleport anywhere");

			pGame->ToggleCJWalk(false); // so we can restore nop
			pGame->ToggleCJWalk(true);
			pGame->SetWorldTime(12, 0);
			pGame->AddToLocalMoney(100000000);
			pGame->GetCamera()->Restore();
			pGame->GetCamera()->SetBehindPlayer();
			pGame->FindPlayerPed()->SetModelIndex(271);
			pGame->FindPlayerPed()->SetHealth(1000.0f);
			pGame->FindPlayerPed()->SetArmour(1000.0f);

			RwMatrix mat = pGame->FindPlayerPed()->m_ped->GetMatrix().ToRwMatrix();
			mat.pos = {2090.6255, 1164.5679, 11.6484};
			pGame->FindPlayerPed()->m_ped->SetMatrix((sa::CMatrix&) mat);
			pGame->FindPlayerPed()->SetTargetRotation(72.8293);
			pGame->NewVehicle(411, mat.pos.x, mat.pos.y + 5.0f, mat.pos.z, 0.0f, false);

			if (!g_saSym->GetSymbol("_ZZ20Menu_SwitchOffToGamevE12bInitWidgets")) {
				spdlog::info("Widgets not inited");
				Memory::callFunction("_Z20Menu_SwitchOffToGamev");
			}
		}

		gameInited = true;
	}

	if (!netgameInited && !offline_mode) {
		spdlog::info("Initializing netgame...");
		pNetGame = new NetGame(Settings::ip(), Settings::port(), Settings::nick(), Settings::pass());
		netgameInited = true;
	}

	if (pNetGame) {
		pNetGame->Process();

		CTextDrawPool* pTextDrawPool = pNetGame->GetTextDrawPool();
		if (pTextDrawPool) {
			pTextDrawPool->Draw();
		}

		if (pAudioStream) {
			pAudioStream->Process();
		}

		if (g_java && !Game::IsGamePaused()) {
			g_java->showWantedLevel(true);
		}
	}
}

void Client::initializeUI()
{
	spdlog::info("Client::initializeUI starting...");
	if (Settings::voice()) {
		for (const auto& deviceInitCallback : Render::deviceInitCallbacks) {
			if (deviceInitCallback != nullptr) {
				deviceInitCallback();
			}
		}
	}

	std::string fonts_path = std::string(Client::gameDir()) + "fonts/";
	spdlog::info("Client::initializeUI creating UI (screen={}x{}, fonts={})...",
		sa::RsGlobal ? sa::RsGlobal->screenWidth : 0,
		sa::RsGlobal ? sa::RsGlobal->screenHeight : 0,
		fonts_path.c_str());

	pUI = new UI(ImVec2(sa::RsGlobal->screenWidth, sa::RsGlobal->screenHeight), fonts_path);
	bool ok = pUI->initialize();
	pUI->performLayout();
	spdlog::info("Client::initializeUI finished (success={})", ok);
}

jint JNI_OnLoad(JavaVM* vm, [[maybe_unused]] void* reserved)
{
	__android_log_print(ANDROID_LOG_INFO, "Ryzn SA-MP", "Ryzn SA-MP library loaded! Build time: " __DATE__ " " __TIME__);

	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}

	g_vm = vm;

	return env->GetVersion();
}
