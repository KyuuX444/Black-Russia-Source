#include "ChatMessenger.h"
#include "../main.h"
#include "../gui/gui.h"
#include "../game/game.h"
#include "../net/netgame.h"
#include "../game/RW/RenderWare.h"
#include "../chatwindow.h"
#include "../playertags.h"
#include "../dialog.h"
#include "../keyboard.h"
#include "../settings.h"
#include "../util/armhook.h"

extern CGUI* pGUI;
extern CChatWindow* pChatWindow;

char CLocalisation::m_szMessages[E_MSG::MSG_COUNT][MAX_LOCALISATION_LENGTH] = {
	"Menghubungkan ke server...",
	"Terhubung. Memuat permainan...",
	"Server menutup koneksi.",
	"{ffffff}Klien {ff0000}Ryzn SA-MP {ffffff}dijalankan.",
	"unused",
	"Anda telah diblokir dari server ini.",
	"Server tidak merespon. Menyambung ulang...",
	"Koneksi ke server terputus. Menyambung ulang...",
	"Server penuh. Menyambung ulang..."
};

void CLocalisation::Initialise(const char* szFile)
{
	Log("Initializing Ryzn SA-MP localisation");
	(void)szFile;
}

char* CLocalisation::GetMessage(E_MSG msg)
{
	return &m_szMessages[msg][0];
}
