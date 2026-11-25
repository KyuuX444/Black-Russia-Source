#include "../UI.h"
#include "../../Game/Game.h"
#include "../../Net/NetGame.h"
#include "../../Java/Java.h"
#include "../../Settings.h"
#include <algorithm>
#include <cctype>

extern UI* pUI;
extern Game* pGame;
extern NetGame* pNetGame;

bool Chat::m_timestamp = false;

Chat::Chat()
		: ListBox(), m_commands()
{
	m_timestamp = Settings::timestamp();
}

void Chat::addChatMessage(const std::string& message, const std::string& nick, const ImColor& nick_color)
{
	std::string full_message = nick + "{FFFFFF}: " + message;
	addMessage(full_message, UI::fixcolor(nick_color));
}

void Chat::addInfoMessage(const std::string& format, ...)
{
	char tmp_buf[512];

	va_list args;
	va_start(args, format);
	vsprintf(tmp_buf, format.c_str(), args);
	va_end(args);

	addMessage(std::string(tmp_buf), ImColor(0x88, 0xFF, 0x62));
}

void Chat::addDebugMessage(const std::string& format, ...)
{
	char tmp_buf[512];

	va_list args;
	va_start(args, format);
	vsprintf(tmp_buf, format.c_str(), args);
	va_end(args);

	addMessage(std::string(tmp_buf), ImColor(0xA9, 0xC4, 0xE4));
}

void Chat::addClientMessage(const std::string& message, const ImColor& color)
{
	addMessage(message, UI::fixcolor(color));
}

void Chat::addMessage(const std::string& message, const ImColor& color)
{
	if (this->itemsCount() > UISettings::chatMaxMessages()) {
		this->removeItem(0);
	}

	auto item = new MessageItem(message, color);
	this->addItem(item);
	this->setScrollY(1.0f);
}

void Chat::addClientCommand(const std::string& cmd_name, const std::function<void(const std::string&)>& callback)
{
	std::string lower = cmd_name;
	std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
	m_commands[lower] = callback;
}

void Chat::clear()
{
	ListBox::clear();
	this->setScrollY(0.0f);
}

void Chat::setPageSize(int pagesize)
{
	if (pagesize < 5) pagesize = 5;
	if (pagesize > 20) pagesize = 20;

	Settings::setChatStrings(pagesize);
	UISettings::m_chatDispMesssages = pagesize + 1;
	UISettings::m_chatSize.y = UISettings::m_chatItemSize.y * UISettings::m_chatDispMesssages;
	this->setFixedSize(UISettings::chatSize());
	this->performLayout();
	this->setScrollY(1.0f);
}

void Chat::updateFontSize(float fontsize)
{
	if (fontsize < 10.0f) fontsize = 10.0f;
	if (fontsize > 60.0f) fontsize = 60.0f;

	Settings::setFontSize(fontsize);
	float scaledFont = fontsize * UISettings::scaleFactor().y;
	UISettings::setFontSize(scaledFont);
	UISettings::m_chatItemSize.y = scaledFont / 2.0f;
	UISettings::m_chatSize.y = UISettings::m_chatItemSize.y * UISettings::m_chatDispMesssages;

	this->setItemSize(UISettings::chatItemSize());
	this->setFixedSize(UISettings::chatSize());

	if (panel()) {
		for (auto* child : panel()->children()) {
			if (auto* item = dynamic_cast<MessageItem*>(child)) {
				item->setFontSize(scaledFont / 2.0f);
			}
		}
	}

	this->performLayout();
	this->setScrollY(1.0f);
}

void Chat::draw(ImGuiRenderer* renderer)
{
	ListBox::draw(renderer);
}

void Chat::activateEvent(bool active)
{
	/*if (active) {
		this->setScrollable(true);
	}
	else {
		this->setScrollable(false);
	}*/
}

void Chat::touchPopEvent()
{
	if (pUI->scoreboard()->visible()) return;
	pUI->keyboard()->show(this);
}

void Chat::keyboardEvent(const std::string& input)
{
	if (input.empty()) return;

	if (input[0] == '/') {
		std::string cmd_name;
		std::string cmd_param;

		size_t space_pos = input.find(' ');
		if (space_pos != std::string::npos) {
			cmd_name = input.substr(1, space_pos - 1);
			cmd_param = input.substr(space_pos + 1);
		}
		else {
			cmd_name = input.substr(1);
		}

		std::transform(cmd_name.begin(), cmd_name.end(), cmd_name.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });

		auto cmd = m_commands.find(cmd_name);
		if (cmd != m_commands.end()) {
			cmd->second(cmd_param);
		}
		else {
			if (pNetGame) {
				pNetGame->SendChatCommand(input.c_str());
			}
		}
	}
	else {
		if (pNetGame) {
			pNetGame->SendChatMessage(input.c_str());
		}
	}
}
