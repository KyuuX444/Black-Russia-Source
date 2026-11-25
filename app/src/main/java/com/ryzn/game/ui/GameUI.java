package com.ryzn.game.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MotionEvent;

import com.ryzn.game.core.GTASA;
import com.ryzn.game.gui.BrDialogWindow;
import com.ryzn.game.ui.widgets.ButtonPanel;
import com.ryzn.game.ui.widgets.Chat;
import com.ryzn.game.ui.widgets.EditObject;
import com.ryzn.game.ui.widgets.HeightProvider;
import com.ryzn.game.ui.widgets.Keyboard;
import com.ryzn.game.ui.widgets.LoadingScreen;
import com.ryzn.game.ui.widgets.Scoreboard;
import com.ryzn.game.ui.widgets.Voice;
import com.ryzn.game.ui.widgets.WantedLevel;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Coordinates 2.00 native C++ UI callbacks with Ryzn's PC Dialog skin and Android overlay widgets.
 */
public class GameUI implements ButtonPanel.Listener, Voice.Listener, Scoreboard.Listener, Keyboard.Listener, Chat.Listener, HeightProvider.Listener {
    private static final String TAG = "RyznGameUI";

    private final GTASA samp;

    private LoadingScreen loadingScreen;
    private ButtonPanel buttonPanel;
    private Voice voice;
    private BrDialogWindow dialog;
    private Keyboard keyboard;
    private Scoreboard scoreboard;
    private WantedLevel wantedLevel;
    private Chat chat;
    private EditObject editObject;
    private HeightProvider heightProvider;

    private native void sendButtonPanelKey(int id);

    private native void voicePressed();

    private native void keyboardSend(byte[] str);

    private native void sendDialogResponse(int dialog_id, int button_id, int list_item, byte[] input);

    private native void sendScoreboardClick(int player_id);

    private native void chatSend(String str);

    public GameUI(GTASA samp) {
        this.samp = samp;
    }

    public void initializeUI() {
        heightProvider = new HeightProvider(samp).init(samp.GetMainView()).setHeightListener(this);
        loadingScreen = new LoadingScreen(samp);
        buttonPanel = new ButtonPanel(samp, this);
        voice = new Voice(samp, this);
        chat = new Chat(samp, this);
        editObject = new EditObject(samp);
        dialog = new BrDialogWindow(samp);
        dialog.setResponseListener(this::_sendDialogResponse);
        scoreboard = new Scoreboard(samp, this);
        keyboard = new Keyboard(samp, this);
        wantedLevel = new WantedLevel(samp);

        if (samp.getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getInt("game_version", 0) == 0) {
            showLoadingScreen(true);
            showWantedLevel(true);
        }
    }

    public void onBackPressed() {
        if (keyboard != null && keyboard.isVisible()) {
            showKeyboard(false);
        }
    }

    public void onTouchEvent(MotionEvent event) {
        if (keyboard != null) {
            keyboard.onTouchEvent(event);
        }
    }

    public void exitGame() {
        samp.finishAndRemoveTask();
        System.exit(0);
    }

    public void onResumeEvent() {
        if (heightProvider != null) {
            heightProvider.init(samp.GetMainView());
        }
    }

    public void showLoadingScreen(boolean z) {
        samp.runOnUiThread(() -> loadingScreen.show(z));
    }

    public void showEditObject(boolean z) {
        samp.runOnUiThread(() -> editObject.show(z));
    }

    public void setPauseState(boolean pause) {
        samp.runOnUiThread(() -> {
            if (pause) {
                keyboard.show(false);
                wantedLevel.show(false);
                dialog.showWithoutReset(false);
                editObject.showWithoutReset(false);
            } else {
                wantedLevel.show(true);
                dialog.showWithoutReset(true);
                editObject.showWithoutReset(true);
            }
        });
    }

    private void showButtonPanel(boolean z) {
        samp.runOnUiThread(() -> buttonPanel.show(z));
    }

    private void showVoice(boolean z) {
        samp.runOnUiThread(() -> voice.show(z));
    }

    public void showDialog(int dialog_id, int dialog_style, String title, String message, String button1, String button2) {
        samp.runOnUiThread(() -> dialog.show(dialog_id, dialog_style, title, message, button1, button2));
    }

    private void showScoreboard(boolean z) {
        samp.runOnUiThread(() -> scoreboard.show(z));
    }

    private void setScoreboardStats(String server, int players) {
        samp.runOnUiThread(() -> scoreboard.setStats(server, players));
    }

    private void addScoreboardPlayer(int id, String name, int score, int ping, String color) {
        samp.runOnUiThread(() -> scoreboard.addPlayer(id, name, score, ping, color));
    }

    public void showWantedLevel(boolean z) {
        samp.runOnUiThread(() -> wantedLevel.show(z));
    }

    public void setWantedLevel(int level) {
        samp.runOnUiThread(() -> wantedLevel.setLevel(level));
    }

    public void showKeyboard(boolean z) {
        samp.runOnUiThread(() -> keyboard.show(z));
    }

    private void showChat(boolean z) {
        samp.runOnUiThread(() -> chat.show(z));
    }

    @Override
    public void _sendButtonPanelKey(int id) {
        sendButtonPanelKey(id);
    }

    @Override
    public void _voicePressed() {
        voicePressed();
    }

    public void _sendDialogResponse(int dialog_id, int button_id, int list_item, byte[] input) {
        Log.i(TAG, "sendDialogResponse " + dialog_id + " btn=" + button_id + " item=" + list_item + " len=" + (input != null ? input.length : 0));
        sendDialogResponse(dialog_id, button_id, list_item, input);
    }

    @Override
    public void _sendScoreboardClick(int player_id) {
        sendScoreboardClick(player_id);
    }

    @Override
    public void _keyboardSend(String str) {
        byte[] ret = new byte[0];
        try {
            ret = str.getBytes("windows-1251");
        } catch (UnsupportedEncodingException e) {
            ret = str.getBytes();
        }
        try {
            keyboardSend(ret);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "keyboardSend failed: " + Arrays.toString(e.getStackTrace()));
        }
    }

    public void addChatMessage(String message, String nick, String nickColor) {
        String formattedMessage = nick + ": " + message;
        SpannableString spannableString = new SpannableString(formattedMessage);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor(nickColor)), 0, nick.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#FFFFFF")), nick.length(), formattedMessage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        samp.runOnUiThread(() -> chat.addMessage(spannableString));
    }

    public void addInfoMessage(String message) {
        SpannableString spannableString = new SpannableString(message);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#00C8C8")), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        samp.runOnUiThread(() -> chat.addMessage(spannableString));
    }

    public void addDebugMessage(String message) {
        SpannableString spannableString = new SpannableString(message);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#F4A419")), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        samp.runOnUiThread(() -> chat.addMessage(spannableString));
    }

    public void addClientMessage(String message, String color) {
        SpannableString spannableString = new SpannableString(message);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor(color)), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        samp.runOnUiThread(() -> chat.addMessage(spannableString));
    }

    @Override
    public void chatShowKeyboard(boolean z) {
    }

    @Override
    public void _chatSend(String str) {
        chatSend(str);
    }

    @Override
    public void onHeightChanged(int orientation, int height) {
        if (keyboard != null) {
            keyboard.onHeightChanged(height);
        }
        if (dialog != null) {
            dialog.onHeightChanged(height);
        }
    }
}
