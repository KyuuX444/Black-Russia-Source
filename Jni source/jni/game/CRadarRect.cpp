#include "../main.h"
#include "game.h"
#include "CWeaponsOutFit.h"
#include "..//chatwindow.h"
#include "..//util/armhook.h"
#include <cmath>
#include <math.h>

RwTexture* CRadarRect::m_pRectTexture = nullptr;
RwTexture* CRadarRect::m_pDiscTexture = nullptr;
bool CRadarRect::m_bEnabled = false;

void CRadarRect::LoadTextures()
{
	//m_pRectTexture = (RwTexture*)LoadTextureFromDB("txd", "radardisc_r");
}

float CRadarRect::CRadar__LimitRadarPoint_hook(float* pos)
{
	if (!pos) return 0.0f;

	if (*(uint8_t*)(g_libGTASA + 0x0063E0B4))
	{
		return sqrtf(pos[0] * pos[0] + pos[1] * pos[1]);
	}

	float dist = sqrtf(pos[0] * pos[0] + pos[1] * pos[1]);
	float maxAxis = fmaxf(fabsf(pos[0]), fabsf(pos[1]));
	if (maxAxis > 1.0f)
	{
		pos[0] /= maxAxis;
		pos[1] /= maxAxis;
	}
	return dist;
}

bool CRadarRect::IsEnabled()
{
	return m_bEnabled;
}

void CRadarRect::SetEnabled(bool bEnabled)
{
	UnFuck(g_libGTASA + 0x003DED84);
	m_bEnabled = bEnabled;
	if (!m_bEnabled)
	{
		*(float*)(g_libGTASA + 0x003DED84) = 1.5708f;
	}
	else
	{
		*(float*)(g_libGTASA + 0x003DED84) = 0.0001f;
	}
}
