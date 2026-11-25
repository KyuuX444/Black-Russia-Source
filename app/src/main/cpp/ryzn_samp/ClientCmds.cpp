#include "ClientCmds.h"
#include "Client.h"
#include "Settings.h"
#include "UI/UI.h"
#include "Java/Java.h"
#include "Game/Game.h"
#include "Game/Patches.h"
#include "Net/NetGame.h"
#include "Game/AudioStream.h"

#include <cstdlib>
#include <cstring>
#include <malloc.h>
#include <sys/sysinfo.h>
#include <thread>

extern UI* pUI;
extern NetGame* pNetGame;
extern Game* pGame;
extern AudioStream* pAudioStream;

void cmdQuit(const std::string& params)
{
	if (pUI) {
		pUI->chat()->addInfoMessage("-> Exiting game...");
	}

	if (pNetGame) {
		if (pNetGame->GetRakClient()) {
			pNetGame->GetRakClient()->Disconnect(0);
		}
	}

	if (g_java) {
		g_java->exitGame();
	}
}

void cmdRcon(const std::string& params)
{
	if (params.empty()) {
		return;
	}

	uint8_t bytePacketId = ID_RCON_COMMAND;
	RakNet::BitStream bsCommand;
	bsCommand.Write(bytePacketId);
	auto dwCmdLen = (uint32_t) strlen(params.c_str());
	bsCommand.Write(dwCmdLen);
	bsCommand.Write(params.c_str(), dwCmdLen);
	pNetGame->GetRakClient()->Send(&bsCommand, HIGH_PRIORITY, RELIABLE, 0);
}

void cmdSave(const std::string& params)
{
	CPlayerPed* pPlayer = pGame->FindPlayerPed();
	FILE* fileOut;
	uint32_t dwVehicleID;
	float fZAngle;
	char szPath[0xFF];

	sprintf(szPath, "%sSAMP/savedpositions.txt", Client::gameDir());
	fileOut = fopen(szPath, "a");

	if (!fileOut) {
		if (pUI) {
			pUI->chat()->addInfoMessage("Save failed");
		}

		return;
	}

	// incar savepos

	if (pPlayer->IsInVehicle()) {

		sa::CVehicle* pVehicle = pPlayer->GetGtaVehicle();

		dwVehicleID = GamePool_Vehicle_GetIndex(pVehicle);
		ScriptCommand(&get_car_z_angle, dwVehicleID, &fZAngle);

		fprintf(fileOut, "AddStaticVehicle(%u, %.4f, %.4f, %.4f, %.4f, %u, %u); // %s\n",
				pVehicle->m_nModelIndex,
				pVehicle->GetPosition().x,
				pVehicle->GetPosition().y,
				pVehicle->GetPosition().z,
				fZAngle, pVehicle->m_nPrimaryColor,
				pVehicle->m_nSecondaryColor,
				params.c_str());

		fclose(fileOut);

		if (pUI) {
			pUI->chat()->addInfoMessage("-> Successfully saved vehicle position");
		}

		return;
	}

	// onfoot savepos

	sa::CPed* pActor = pPlayer->m_ped;
	ScriptCommand(&get_actor_z_angle, pPlayer->m_gtaId, &fZAngle);

	fprintf(fileOut, "AddPlayerClass(%u, %.4f, %.4f, %.4f, %.4f, 0, 0, 0, 0, 0, 0); // %s\n",
			pPlayer->GetModelIndex(),
			pActor->GetPosition().x,
			pActor->GetPosition().y,
			pActor->GetPosition().z,
			fZAngle,
			params.c_str());

	if (pUI) {
		pUI->chat()->addInfoMessage("-> Successfully saved onfoot position");
	}

	fclose(fileOut);
}

void cmdDL(const std::string& params)
{
	if (pUI) {
		pUI->chat()->addInfoMessage("-> DebugLabels %s", pUI->debuglabels()->visible() ? "OFF" : "ON");
		pUI->debuglabels()->setVisible(!pUI->debuglabels()->visible());
	}
}

void cmdDW(const std::string& params)
{
	if (pUI) {
		pUI->chat()->addInfoMessage("-> DeathWindow: %s", pUI->deathwindow()->visible() ? "OFF" : "ON");
		pUI->deathwindow()->setVisible(!pUI->deathwindow()->visible());
	}
}

void cmdClearMyChat(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	pUI->chat()->clear();
}

void cmdReconnect(const std::string& params)
{
	if (pNetGame) {
		pNetGame->Reconnect();
	}
}

void cmdPageSize(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	if (params.empty()) {
		pUI->chat()->addInfoMessage("-> Usage: /pagesize [5-20] (current: %d)", Settings::chatstrings());
		return;
	}

	char* endPtr = nullptr;
	long val = std::strtol(params.c_str(), &endPtr, 10);
	if (endPtr == params.c_str() || val < 5 || val > 20) {
		pUI->chat()->addInfoMessage("-> Pagesize must be between 5 and 20");
		return;
	}

	pUI->chat()->setPageSize(static_cast<int>(val));
	pUI->chat()->addInfoMessage("-> Pagesize set to %ld", val);
}

void cmdFontSize(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	if (params.empty()) {
		pUI->chat()->addInfoMessage("-> Usage: /fontsize [10-60 or -3..5] (current: %.1f)", Settings::fontsize());
		return;
	}

	char* endPtr = nullptr;
	float val = std::strtof(params.c_str(), &endPtr);
	if (endPtr == params.c_str()) {
		pUI->chat()->addInfoMessage("-> Invalid fontsize");
		return;
	}

	if (val >= -3.0f && val <= 5.0f) {
		val = 30.0f + val * 3.0f;
	}

	if (val < 10.0f || val > 60.0f) {
		pUI->chat()->addInfoMessage("-> Fontsize must be between 10 and 60 (or -3..5)");
		return;
	}

	pUI->chat()->updateFontSize(val);
	pUI->chat()->addInfoMessage("-> Fontsize set to %.1f", val);
}

void cmdTimestamp(const std::string& params)
{
	if (pUI && pUI->chat()) {
		bool next = !Chat::timestamp();
		Chat::setTimestamp(next);
		Settings::setTimestamp(next);
		pUI->chat()->performLayout();
		pUI->chat()->addInfoMessage("-> Timestamp %s", next ? "ON" : "OFF");
	}
}

void cmdFpsLimit(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	if (params.empty()) {
		pUI->chat()->addInfoMessage("-> Usage: /fpslimit [20-120] (current: %d)", Settings::fpslimit());
		return;
	}

	char* endPtr = nullptr;
	long fps = std::strtol(params.c_str(), &endPtr, 10);
	if (endPtr == params.c_str() || fps < 20 || fps > 120) {
		pUI->chat()->addInfoMessage("-> FPS limit must be between 20 and 120");
		return;
	}

	Patches::setFpsLimit(static_cast<int>(fps));
	pUI->chat()->addInfoMessage("-> Frame limiter set to %ld FPS", fps);
}

void cmdHeadMove(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	bool next = !Settings::headmove();
	Settings::setHeadMove(next);
	pUI->chat()->addInfoMessage("-> Headmove %s", next ? "ON" : "OFF");
}

void cmdInterior(const std::string& params)
{
	if (!pUI || !pUI->chat() || !pGame) {
		return;
	}

	pUI->chat()->addInfoMessage("-> Current interior: %d", static_cast<int>(pGame->GetActiveInterior()));
}

void cmdMem(const std::string& params)
{
	if (!pUI || !pUI->chat()) {
		return;
	}

	struct mallinfo2 memInfo = mallinfo2();
	long long allocMB = static_cast<long long>(memInfo.uordblks) / (1024 * 1024);
	long long totalMB = 0;

#if VER_x32
	FILE* meminfo = fopen("/proc/meminfo", "r");
	if (meminfo) {
		char line[256];
		while (fgets(line, sizeof(line), meminfo)) {
			if (strncmp(line, "MemTotal:", 9) == 0) {
				totalMB = atoll(&line[9]) / 1024;
				break;
			}
		}
		fclose(meminfo);
	}
#else
	struct sysinfo info{};
	if (sysinfo(&info) == 0) {
		totalMB = static_cast<long long>(info.totalram) / (1024 * 1024);
	}
#endif

	pUI->chat()->addInfoMessage("-> Memory: %lld MB used / %lld MB RAM", allocMB, totalMB);
}

void cmdCmds(const std::string& params)
{
	if (pUI && pUI->chat()) {
		pUI->chat()->addInfoMessage("--- Ryzn SA-MP Client Commands ---");
		pUI->chat()->addInfoMessage("/rec (/reconnect) - Reconnect to server | /q (/quit) - Exit game");
		pUI->chat()->addInfoMessage("/c (/clear) - Clear chat | /pagesize [5-20] | /fontsize [10-60|-3..5]");
		pUI->chat()->addInfoMessage("/timestamp - Chat timestamps | /fpslimit [20-120] | /headmove - Ped head look");
		pUI->chat()->addInfoMessage("/dl - Vehicle debug | /dw - Killfeed | /save (/rs) - Save coords");
		pUI->chat()->addInfoMessage("/interior - Interior ID | /mem - Memory stats | /rcon [cmd]");
	}
}

void cmdJetpack(const std::string& params)
{
	if (pGame->FindPlayerPed()->IsInVehicle()) {
		pUI->chat()->addInfoMessage("-> You can't use jetpack while in vehicle");
		return;
	}

	pGame->FindPlayerPed()->StartJetpack();
	pUI->chat()->addInfoMessage("-> Jetpack started");
}

void ClientCmds::initialize()
{
	spdlog::info("Initializing client commands..");

	auto* chat = pUI->chat();
	chat->addClientCommand("q", cmdQuit);
	chat->addClientCommand("quit", cmdQuit);
	chat->addClientCommand("c", cmdClearMyChat);
	chat->addClientCommand("cc", cmdClearMyChat);
	chat->addClientCommand("clear", cmdClearMyChat);
	chat->addClientCommand("clearmychat", cmdClearMyChat);
	chat->addClientCommand("pagesize", cmdPageSize);
	chat->addClientCommand("fontsize", cmdFontSize);
	chat->addClientCommand("fs", cmdFontSize);
	chat->addClientCommand("timestamp", cmdTimestamp);
	chat->addClientCommand("fpslimit", cmdFpsLimit);
	chat->addClientCommand("headmove", cmdHeadMove);
	chat->addClientCommand("interior", cmdInterior);
	chat->addClientCommand("mem", cmdMem);
	chat->addClientCommand("save", cmdSave);
	chat->addClientCommand("rs", cmdSave);
	chat->addClientCommand("help", cmdCmds);
	chat->addClientCommand("cmds", cmdCmds);
	chat->addClientCommand("clientcmds", cmdCmds);

	if (Client::offlineMode()) {
		chat->addClientCommand("j", cmdJetpack);
	}
	else {
		chat->addClientCommand("rec", cmdReconnect);
		chat->addClientCommand("reconnect", cmdReconnect);
		chat->addClientCommand("rcon", cmdRcon);
		chat->addClientCommand("dl", cmdDL);
		chat->addClientCommand("dw", cmdDW);
	}
}
