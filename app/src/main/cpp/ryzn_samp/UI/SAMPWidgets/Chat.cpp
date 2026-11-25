#include "../UI.h"
#include "../../Game/Game.h"
#include "../../Net/NetGame.h"
#include "../../Java/Java.h"
#include "../../Settings.h"
#include <algorithm>
#include <cctype>
#include <cmath>

extern UI* pUI;
extern Game* pGame;
extern NetGame* pNetGame;

bool Chat::m_timestamp = false;

Chat::Chat()
		: ListBox(), m_commands(), m_touchTarget(TouchTarget::NONE),
		  m_pushPos(0.0f, 0.0f), m_lastDragPos(0.0f, 0.0f), m_wasDragged(false)
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
	vsnprintf(tmp_buf, sizeof(tmp_buf), format.c_str(), args);
	va_end(args);

	addMessage(std::string(tmp_buf), ImColor(0x88, 0xFF, 0x62));
}

void Chat::addDebugMessage(const std::string& format, ...)
{
	char tmp_buf[512];

	va_list args;
	va_start(args, format);
	vsnprintf(tmp_buf, sizeof(tmp_buf), format.c_str(), args);
	va_end(args);

	addMessage(std::string(tmp_buf), ImColor(0xA9, 0xC4, 0xE4));
}

void Chat::addClientMessage(const std::string& message, const ImColor& color)
{
	addMessage(message, UI::fixcolor(color));
}

void Chat::addMessage(const std::string& message, const ImColor& color)
{
	bool followBottom = this->isAtBottom();

	if (this->itemsCount() >= UISettings::chatMaxMessages()) {
		this->removeItem(0);
	}

	auto item = new MessageItem(message, color);
	this->addItem(item);

	if (followBottom) {
		this->scrollToBottom();
	}
	else {
		this->clampScroll();
	}
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
	this->scrollToBottom();
}

void Chat::setPageSize(int pagesize)
{
	if (pagesize < 5) pagesize = 5;
	if (pagesize > 20) pagesize = 20;

	Settings::setChatStrings(pagesize);
	UISettings::m_chatDispMesssages = pagesize + 1;
	UISettings::m_chatSize.y = UISettings::m_chatItemSize.y * UISettings::m_chatDispMesssages;
	this->setFixedSize(UISettings::chatSize());
	ListBox::performLayout();
	this->scrollToBottom();
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

	ListBox::performLayout();
	this->scrollToBottom();
}

float Chat::maxScrollOffset() const
{
	if (!panel()) return 0.0f;
	return std::max(0.0f, panel()->height() - height());
}

float Chat::scrollFraction() const
{
	float maxScroll = maxScrollOffset();
	if (maxScroll <= 0.5f || !panel()) return 1.0f;
	float curOffset = -panel()->position().y;
	return std::clamp(curOffset / maxScroll, 0.0f, 1.0f);
}

void Chat::setScrollFraction(float fraction)
{
	if (!panel()) return;
	float maxScroll = maxScrollOffset();
	if (maxScroll <= 0.5f) {
		panel()->setPosition(ImVec2(0.0f, 0.0f));
		return;
	}
	float clamped = std::clamp(fraction, 0.0f, 1.0f);
	panel()->setPosition(ImVec2(0.0f, -clamped * maxScroll));
}

bool Chat::isAtBottom() const
{
	float maxScroll = maxScrollOffset();
	if (maxScroll <= 0.5f || !panel()) return true;
	float curOffset = -panel()->position().y;
	float threshold = std::max(4.0f, UISettings::chatItemSize().y * 0.6f);
	return (maxScroll - curOffset) <= threshold;
}

void Chat::clampScroll()
{
	if (!panel()) return;
	float maxScroll = maxScrollOffset();
	if (maxScroll <= 0.5f) {
		panel()->setPosition(ImVec2(0.0f, 0.0f));
		return;
	}
	float y = std::clamp(panel()->position().y, -maxScroll, 0.0f);
	panel()->setPosition(ImVec2(0.0f, y));
}

void Chat::scrollToBottom()
{
	setScrollFraction(1.0f);
}

void Chat::scrollByLines(int deltaLines)
{
	if (!panel()) return;
	float maxScroll = maxScrollOffset();
	if (maxScroll <= 0.5f) return;
	float curOffset = -panel()->position().y;
	float step = UISettings::chatItemSize().y * static_cast<float>(deltaLines);
	float nextOffset = std::clamp(curOffset + step, 0.0f, maxScroll);
	panel()->setPosition(ImVec2(0.0f, -nextOffset));
}

void Chat::performLayout()
{
	bool keepBottom = isAtBottom();
	float prevOffset = panel() ? -panel()->position().y : 0.0f;
	ListBox::performLayout();
	if (keepBottom) {
		scrollToBottom();
	}
	else if (panel()) {
		float maxScroll = maxScrollOffset();
		panel()->setPosition(ImVec2(0.0f, -std::clamp(prevOffset, 0.0f, maxScroll)));
	}
}

bool Chat::isInputOpenForChat() const
{
	return pUI && pUI->keyboard() && pUI->keyboard()->visible() && pUI->keyboard()->caller() == this;
}

Chat::PcLayout Chat::computePcLayout() const
{
	PcLayout layout{};
	layout.inputFocused = isInputOpenForChat();
	bool overflow = maxScrollOffset() > 1.0f;
	bool pcEnabled = Settings::pcchatbox();

	layout.showScrollbar = pcEnabled && (overflow || layout.inputFocused);
	layout.showJumpBottom = pcEnabled && !isAtBottom();
	layout.showInputBar = pcEnabled && layout.inputFocused;

	ImVec2 boxPos = absolutePosition();
	ImVec2 boxSize = size();
	float fs = UISettings::fontSize();

	float sbW = std::clamp(fs * 0.62f, 16.0f, 28.0f);
	float sbGap = std::max(4.0f, sbW * 0.30f);
	float sbX = std::max(4.0f, boxPos.x - sbW - sbGap);
	layout.sbWidth = sbW;

	layout.sbUpBtn = {ImVec2(sbX, boxPos.y), ImVec2(sbX + sbW, boxPos.y + sbW)};
	layout.sbDownBtn = {ImVec2(sbX, boxPos.y + boxSize.y - sbW), ImVec2(sbX + sbW, boxPos.y + boxSize.y)};

	float trackTop = boxPos.y + sbW + 2.0f;
	float trackBottom = boxPos.y + boxSize.y - sbW - 2.0f;
	if (trackBottom < trackTop + 8.0f) trackBottom = trackTop + 8.0f;
	layout.sbTrack = {ImVec2(sbX, trackTop), ImVec2(sbX + sbW, trackBottom)};

	float trackH = trackBottom - trackTop;
	float panelH = panel() ? std::max(boxSize.y, panel()->height()) : boxSize.y;
	float thumbH = overflow
			? std::max(sbW * 1.15f, trackH * std::min(1.0f, boxSize.y / panelH))
			: trackH;
	thumbH = std::min(thumbH, trackH);
	float frac = overflow ? scrollFraction() : 1.0f;
	float thumbTop = trackTop + frac * std::max(0.0f, trackH - thumbH);
	layout.sbThumb = {ImVec2(sbX + 2.0f, thumbTop), ImVec2(sbX + sbW - 2.0f, thumbTop + thumbH)};

	float jumpH = std::max(18.0f, fs * 0.68f);
	float jumpW = std::max(64.0f, fs * 2.75f);
	layout.jumpBtn = {
			ImVec2(boxPos.x + boxSize.x - jumpW - 4.0f, boxPos.y + boxSize.y - jumpH - 4.0f),
			ImVec2(boxPos.x + boxSize.x - 4.0f, boxPos.y + boxSize.y - 4.0f)
	};

	float barY = boxPos.y + boxSize.y + 5.0f;
	float barH = std::max(26.0f, fs * 0.96f);
	float btnGap = 4.0f;
	float btnW = barH * 1.15f;
	float sendW = barH * 1.85f;
	float barRight = boxPos.x + boxSize.x;

	layout.sendBtn = {ImVec2(barRight - sendW, barY), ImVec2(barRight, barY + barH)};
	layout.histNextBtn = {ImVec2(layout.sendBtn.min.x - btnGap - btnW, barY), ImVec2(layout.sendBtn.min.x - btnGap, barY + barH)};
	layout.histPrevBtn = {ImVec2(layout.histNextBtn.min.x - btnGap - btnW, barY), ImVec2(layout.histNextBtn.min.x - btnGap, barY + barH)};
	layout.slashBtn = {ImVec2(layout.histPrevBtn.min.x - btnGap - btnW, barY), ImVec2(layout.histPrevBtn.min.x - btnGap, barY + barH)};
	layout.inputBox = {ImVec2(boxPos.x, barY), ImVec2(std::max(boxPos.x + 40.0f, layout.slashBtn.min.x - btnGap), barY + barH)};
	layout.fullBarRect = {ImVec2(boxPos.x, barY), ImVec2(barRight, barY + barH)};

	return layout;
}

bool Chat::handlesTouch(const ImVec2& pos) const
{
	if (!visible()) return false;
	if (m_touchTarget != TouchTarget::NONE) return true;
	if (contains(pos)) return true;

	PcLayout layout = computePcLayout();
	if (layout.showScrollbar) {
		PcRect sbFull = {layout.sbUpBtn.min, layout.sbDownBtn.max};
		if (sbFull.contains(pos, 10.0f)) return true;
	}
	if (layout.showJumpBottom && layout.jumpBtn.contains(pos, 6.0f)) return true;
	if (layout.showInputBar && layout.fullBarRect.contains(pos, 4.0f)) return true;
	return false;
}

void Chat::touchEvent(const ImVec2& pos, TouchType type)
{
	PcLayout layout = computePcLayout();

	if (type == TouchType::push) {
		m_pushPos = pos;
		m_lastDragPos = pos;
		m_wasDragged = false;
		m_touchTarget = TouchTarget::NONE;

		if (layout.showJumpBottom && layout.jumpBtn.contains(pos, 6.0f)) {
			m_touchTarget = TouchTarget::JUMP_BOTTOM;
		}
		else if (layout.showInputBar && layout.fullBarRect.contains(pos, 4.0f)) {
			if (layout.sendBtn.contains(pos, 2.0f)) m_touchTarget = TouchTarget::INPUT_SEND;
			else if (layout.histNextBtn.contains(pos, 2.0f)) m_touchTarget = TouchTarget::INPUT_HIST_NEXT;
			else if (layout.histPrevBtn.contains(pos, 2.0f)) m_touchTarget = TouchTarget::INPUT_HIST_PREV;
			else if (layout.slashBtn.contains(pos, 2.0f)) m_touchTarget = TouchTarget::INPUT_SLASH;
		}
		else if (layout.showScrollbar && layout.sbUpBtn.contains(pos, 8.0f)) {
			m_touchTarget = TouchTarget::SB_UP;
		}
		else if (layout.showScrollbar && layout.sbDownBtn.contains(pos, 8.0f)) {
			m_touchTarget = TouchTarget::SB_DOWN;
		}
		else if (layout.showScrollbar && layout.sbTrack.contains(pos, 10.0f)) {
			m_touchTarget = TouchTarget::SB_THUMB;
			float thumbH = layout.sbThumb.max.y - layout.sbThumb.min.y;
			float span = std::max(1.0f, (layout.sbTrack.max.y - layout.sbTrack.min.y) - thumbH);
			float frac = (pos.y - layout.sbTrack.min.y - thumbH * 0.5f) / span;
			setScrollFraction(frac);
		}
		else if (contains(pos)) {
			m_touchTarget = TouchTarget::BODY;
		}
	}
	else if (type == TouchType::move) {
		if (m_touchTarget == TouchTarget::SB_THUMB) {
			float thumbH = layout.sbThumb.max.y - layout.sbThumb.min.y;
			float span = std::max(1.0f, (layout.sbTrack.max.y - layout.sbTrack.min.y) - thumbH);
			float frac = (pos.y - layout.sbTrack.min.y - thumbH * 0.5f) / span;
			setScrollFraction(frac);
		}
		else if (m_touchTarget == TouchTarget::BODY) {
			float dy = pos.y - m_lastDragPos.y;
			m_lastDragPos = pos;
			if (std::fabs(pos.y - m_pushPos.y) > 6.0f || std::fabs(pos.x - m_pushPos.x) > 6.0f) {
				m_wasDragged = true;
			}
			if (m_wasDragged && panel() && maxScrollOffset() > 0.5f) {
				float nextY = std::clamp(panel()->position().y + dy, -maxScrollOffset(), 0.0f);
				panel()->setPosition(ImVec2(0.0f, nextY));
			}
		}
	}
	else if (type == TouchType::pop) {
		switch (m_touchTarget) {
			case TouchTarget::JUMP_BOTTOM:
				if (layout.showJumpBottom && layout.jumpBtn.contains(pos, 8.0f)) {
					scrollToBottom();
				}
				break;
			case TouchTarget::SB_UP:
				if (layout.sbUpBtn.contains(pos, 10.0f)) {
					scrollByLines(-3);
				}
				break;
			case TouchTarget::SB_DOWN:
				if (layout.sbDownBtn.contains(pos, 10.0f)) {
					scrollByLines(3);
				}
				break;
			case TouchTarget::INPUT_SLASH:
				if (pUI && pUI->keyboard() && pUI->keyboard()->input()) {
					pUI->keyboard()->input()->setInputString("/");
				}
				break;
			case TouchTarget::INPUT_HIST_PREV:
				if (pUI && pUI->keyboard() && pUI->keyboard()->input() && pUI->keyboard()->history()) {
					pUI->keyboard()->input()->setInputString(pUI->keyboard()->history()->next());
				}
				break;
			case TouchTarget::INPUT_HIST_NEXT:
				if (pUI && pUI->keyboard() && pUI->keyboard()->input() && pUI->keyboard()->history()) {
					pUI->keyboard()->input()->setInputString(pUI->keyboard()->history()->prev());
				}
				break;
			case TouchTarget::INPUT_SEND:
				if (pUI && pUI->keyboard()) {
					pUI->keyboard()->send();
				}
				break;
			case TouchTarget::BODY:
				if (!m_wasDragged && contains(pos)) {
					touchPopEvent();
				}
				break;
			default:
				break;
		}
		m_touchTarget = TouchTarget::NONE;
		m_wasDragged = false;
	}
}

void Chat::drawScrollBar(ImGuiRenderer* renderer, const PcLayout& layout) const
{
	bool activeState = layout.inputFocused || !isAtBottom() || m_touchTarget == TouchTarget::SB_THUMB;
	int bgAlpha = activeState ? 185 : 115;
	int borderAlpha = activeState ? 140 : 70;
	int thumbAlpha = activeState ? 225 : 155;

	ImColor btnBg(15, 18, 26, bgAlpha);
	ImColor trackBg(10, 12, 18, activeState ? 150 : 85);
	ImColor borderCol(255, 255, 255, borderAlpha);
	ImColor thumbCol = (m_touchTarget == TouchTarget::SB_THUMB)
			? ImColor(99, 102, 241, 240)
			: ImColor(228, 234, 245, thumbAlpha);
	ImColor arrowCol(245, 248, 255, activeState ? 235 : 170);

	// Up button + triangle
	renderer->drawRect(layout.sbUpBtn.min, layout.sbUpBtn.max, btnBg, true);
	renderer->drawRect(layout.sbUpBtn.min, layout.sbUpBtn.max, borderCol, false, 1.0f);
	float pad = layout.sbWidth * 0.28f;
	renderer->drawTriangle(
			ImVec2((layout.sbUpBtn.min.x + layout.sbUpBtn.max.x) * 0.5f, layout.sbUpBtn.min.y + pad),
			ImVec2(layout.sbUpBtn.min.x + pad, layout.sbUpBtn.max.y - pad),
			ImVec2(layout.sbUpBtn.max.x - pad, layout.sbUpBtn.max.y - pad),
			arrowCol, true);

	// Track + Thumb
	renderer->drawRect(layout.sbTrack.min, layout.sbTrack.max, trackBg, true);
	renderer->drawRect(layout.sbTrack.min, layout.sbTrack.max, borderCol, false, 1.0f);
	renderer->drawRect(layout.sbThumb.min, layout.sbThumb.max, thumbCol, true);

	// Down button + triangle
	renderer->drawRect(layout.sbDownBtn.min, layout.sbDownBtn.max, btnBg, true);
	renderer->drawRect(layout.sbDownBtn.min, layout.sbDownBtn.max, borderCol, false, 1.0f);
	renderer->drawTriangle(
			ImVec2(layout.sbDownBtn.min.x + pad, layout.sbDownBtn.min.y + pad),
			ImVec2(layout.sbDownBtn.max.x - pad, layout.sbDownBtn.min.y + pad),
			ImVec2((layout.sbDownBtn.min.x + layout.sbDownBtn.max.x) * 0.5f, layout.sbDownBtn.max.y - pad),
			arrowCol, true);
}

void Chat::drawJumpToBottomButton(ImGuiRenderer* renderer, const PcLayout& layout) const
{
	renderer->drawRect(layout.jumpBtn.min, layout.jumpBtn.max, ImColor(99, 102, 241, 220), true);
	renderer->drawRect(layout.jumpBtn.min, layout.jumpBtn.max, ImColor(255, 255, 255, 190), false, 1.0f);

	float textSz = UISettings::fontSize() * 0.44f;
	std::string label = "Latest";
	ImVec2 tSz = renderer->calculateTextSize(label, textSz);
	ImVec2 center(
			(layout.jumpBtn.min.x + layout.jumpBtn.max.x - tSz.x) * 0.5f,
			(layout.jumpBtn.min.y + layout.jumpBtn.max.y - tSz.y) * 0.5f
	);
	renderer->drawText(center, ImColor(255, 255, 255, 255), label, false, textSz);
}

void Chat::drawInputActionBar(ImGuiRenderer* renderer, const PcLayout& layout) const
{
	float textSz = UISettings::fontSize() * 0.52f;
	ImColor boxBg(14, 17, 26, 220);
	ImColor accentCol(99, 102, 241, 240);
	ImColor btnBorder(255, 255, 255, 150);

	// Input bar container
	renderer->drawRect(layout.inputBox.min, layout.inputBox.max, boxBg, true);
	renderer->drawRect(layout.inputBox.min, layout.inputBox.max, accentCol, false, 1.5f);

	std::string currentUtf8;
	if (pUI && pUI->keyboard() && pUI->keyboard()->input()) {
		currentUtf8 = Encoding::cp2utf(pUI->keyboard()->input()->inputString());
	}
	bool cursorOn = std::fmod(ImGui::GetTime(), 1.0) < 0.55;
	std::string display = "> " + currentUtf8 + (cursorOn ? "|" : "");
	float padX = 7.0f;
	ImVec2 tPos(layout.inputBox.min.x + padX, (layout.inputBox.min.y + layout.inputBox.max.y - textSz) * 0.5f);
	renderer->pushClipRect(layout.inputBox.min + ImVec2(2.0f, 1.0f), layout.inputBox.max - ImVec2(2.0f, 1.0f), true);
	renderer->drawText(tPos, ImColor(255, 255, 255, 255), display, false, textSz);
	renderer->popClipRect();

	auto drawActionBtn = [&](const PcRect& r, const char* txt, const ImColor& fill) {
		renderer->drawRect(r.min, r.max, fill, true);
		renderer->drawRect(r.min, r.max, btnBorder, false, 1.0f);
		ImVec2 sz = renderer->calculateTextSize(txt, textSz);
		renderer->drawText(
				ImVec2((r.min.x + r.max.x - sz.x) * 0.5f, (r.min.y + r.max.y - sz.y) * 0.5f),
				ImColor(255, 255, 255, 255), txt, false, textSz);
	};

	drawActionBtn(layout.slashBtn, "/", boxBg);
	drawActionBtn(layout.histPrevBtn, "Up", boxBg);
	drawActionBtn(layout.histNextBtn, "Dn", boxBg);
	drawActionBtn(layout.sendBtn, "Send", accentCol);
}

void Chat::draw(ImGuiRenderer* renderer)
{
	PcLayout layout = computePcLayout();
	ImVec2 boxPos = absolutePosition();
	ImVec2 boxSize = size();

	if (Settings::pcchatbox() && (layout.inputFocused || m_touchTarget == TouchTarget::BODY || m_touchTarget == TouchTarget::SB_THUMB)) {
		renderer->drawRect(boxPos - ImVec2(4.0f, 2.0f), boxPos + boxSize + ImVec2(4.0f, 2.0f), ImColor(10, 12, 18, 105), true);
		renderer->drawRect(boxPos - ImVec2(4.0f, 2.0f), boxPos + boxSize + ImVec2(4.0f, 2.0f), ImColor(255, 255, 255, 45), false, 1.0f);
	}

	renderer->pushClipRect(boxPos, boxPos + boxSize, true);
	ListBox::draw(renderer);
	renderer->popClipRect();

	if (layout.showScrollbar) {
		drawScrollBar(renderer, layout);
	}
	if (layout.showJumpBottom) {
		drawJumpToBottomButton(renderer, layout);
	}
	if (layout.showInputBar) {
		drawInputActionBar(renderer, layout);
	}
}

void Chat::activateEvent(bool active)
{
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
