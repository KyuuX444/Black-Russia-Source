//
// Created by ALYN on 16-Jan-25.
//

#include "Game.h"
#include "RemoveBuilding.h"

void RemoveBuilding::add(uint32_t model, sa::CVector pos, float radius)
{
	spdlog::debug("RemoveBuilding::add {}, ({}, {}, {}), {}", model, pos.x, pos.y, pos.z, radius);

	if (model == 19300) {
		return;
	}

	if (m_buildingToRemoveCount >= 1000) {
		return;
	}

	removeObjectInRange(model, pos, radius);

	m_buildingToRemove[m_buildingToRemoveCount].model = model;
	m_buildingToRemove[m_buildingToRemoveCount].pos = pos;
	m_buildingToRemove[m_buildingToRemoveCount].radius = radius;
	m_buildingToRemoveCount++;
}

void RemoveBuilding::removeObjectInRange(uint32_t model, sa::CVector pos, float radius)
{
	static bool s_fullMapOccludersCleared = false;
	if (!s_fullMapOccludersCleared) {
		removeOccludersInRadius(pos, 20000.0f);
		s_fullMapOccludersCleared = true;
	}

	typedef sa::CPool<sa::CEntity, sa::CEntity> CPool;
	const float radiusSq = radius * radius;
	auto processPool = [&](CPool* pool) {
		if (!pool) return;
		for (int i = 0; i < pool->m_nSize; i++) {
			sa::CEntity* pEntity = pool->GetAt(i);
			if (!pEntity) continue;
			if (model != static_cast<uint32_t>(-1) && pEntity->m_nModelIndex != model) continue;
			sa::CVector entPos = pEntity->GetPosition();
			float dx = entPos.x - pos.x;
			if (dx < -radius || dx > radius) continue;
			float dy = entPos.y - pos.y;
			if (dy < -radius || dy > radius) continue;
			float dz = entPos.z - pos.z;
			if (dz < -radius || dz > radius) continue;
			if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
				pEntity->m_placement.m_vPosn.z -= 2000.0f;
			}
		}
	};

	static CPool** ppBuildingPool = g_saSym->GetSymbol<CPool**>("_ZN6CPools16ms_pBuildingPoolE");
	if (ppBuildingPool && *ppBuildingPool) {
		processPool(*ppBuildingPool);
	}

	static CPool** ppDummyPool = g_saSym->GetSymbol<CPool**>("_ZN6CPools13ms_pDummyPoolE");
	if (ppDummyPool && *ppDummyPool) {
		processPool(*ppDummyPool);
	}

	static CPool** ppObjectPool = g_saSym->GetSymbol<CPool**>("_ZN6CPools14ms_pObjectPoolE");
	if (ppObjectPool && *ppObjectPool) {
		processPool(*ppObjectPool);
	}
}

class COccluder {
public:
	unsigned short fMidX;
	unsigned short fMidY;
	unsigned short fMidZ;
	unsigned short fWidthX;
	unsigned short fWidthY;
	unsigned short fHeight;
	char cRotZ;
	char cRotY;
	char cRotX;
	char cPad;
	unsigned short nFlags;
};
VALIDATE_SIZE(COccluder, 0x12);

COccluder* aOccluders = nullptr;
int32_t NumOccludersOnMap = 0;

void RemoveBuilding::removeOccludersInRadius(sa::CVector pos, float radius)
{
	if (!aOccluders || NumOccludersOnMap < 1) {
		return;
	}
	const float radiusSq = radius * radius;
	for (int i = 0; i < NumOccludersOnMap; i++) {
		float ox = static_cast<float>(aOccluders[i].fMidX) * 0.25f;
		float oy = static_cast<float>(aOccluders[i].fMidY) * 0.25f;
		float oz = static_cast<float>(aOccluders[i].fMidZ) * 0.25f;
		float dx = ox - pos.x;
		float dy = oy - pos.y;
		float dz = oz - pos.z;
		if ((dx * dx + dy * dy + dz * dz) <= radiusSq) {
			aOccluders[i].fMidX = 0;
			aOccluders[i].fMidY = 0;
			aOccluders[i].fMidZ = 0;
			aOccluders[i].fWidthX = 0;
			aOccluders[i].fWidthY = 0;
			aOccluders[i].fHeight = 0;
		}
	}
}

bool RemoveBuilding::needsRemove(uint32_t model, sa::CVector pos)
{
	if (m_buildingToRemoveCount == 0) {
		return false;
	}

	for (int i = 0; i < m_buildingToRemoveCount; i++) {
		const auto& entry = m_buildingToRemove[i];
		if (entry.model == static_cast<uint32_t>(-1) || entry.model == model) {
			float r = entry.radius;
			float dx = pos.x - entry.pos.x;
			if (dx < -r || dx > r) continue;
			float dy = pos.y - entry.pos.y;
			if (dy < -r || dy > r) continue;
			float dz = pos.z - entry.pos.z;
			if (dz < -r || dz > r) continue;
			if ((dx * dx + dy * dy + dz * dz) <= (r * r)) {
				return true;
			}
		}
	}

	return false;
}

DECL_HOOK(void, CFileLoader_LoadObjectInstance, sa::CFileObjectInstance* instance, char* name)
{
	sa::CVector* pos = &instance->m_vecPosition;
	int* model = &instance->m_nModelId;

	if (instance && RemoveBuilding::needsRemove(*model, pos)) {
		spdlog::debug("RemoveBuilding hook: model {}", *model);
		*model = 19300;
	}

	CFileLoader_LoadObjectInstance(instance, name);
}

void RemoveBuilding::installHooks()
{
	spdlog::info("Installing remove building hooks...");

	aOccluders = *g_saSym->GetSymbol<COccluder**>("_ZN10COcclusion10aOccludersE");
	NumOccludersOnMap = *g_saSym->GetSymbol<int32_t*>("_ZN10COcclusion17NumOccludersOnMapE");

	HOOK("_ZN11CFileLoader18LoadObjectInstanceEP19CFileObjectInstancePKc", CFileLoader_LoadObjectInstance);
}
