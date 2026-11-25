#pragma once

class Patches {
public:
	static void preApply();
	static void apply();
	static void setFpsLimit(int fps);

	static void allocPlayers();
	static void initBetterPoolsSize();
};
