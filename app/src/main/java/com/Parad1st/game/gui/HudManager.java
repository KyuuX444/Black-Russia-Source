package com.byparad1st.game.gui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.byparad1st.game.R;
import com.byparad1st.game.gui.util.Utils;
import com.byparad1st.launcher.other.AlyaLiteDownloader;
import com.nvidia.devtech.NvEventQueueActivity;

import org.ini4j.Wini;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HudManager {
    public Activity activity;
    public ConstraintLayout hud_layout;
    public LinearLayout chatBox;
    public RecyclerView chat;
    public ChatAdapter adapter;
    public final ArrayList<Spanned> chat_lines = new ArrayList<>();

    private Button bpExtend;
    private HorizontalScrollView bpScroll;
    private ImageButton voiceButton;
    private boolean panelExtended = false;
    public int keybo = 0;

    private boolean showVoiceButton = true;
    private boolean showTimestamps = false;
    private int maxChatHistory = 60;
    private float chatTextSizeSp = 11.5f;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public HudManager(Activity aactivity) {
        this.activity = aactivity;
        this.hud_layout = aactivity.findViewById(R.id.hud_main);
        this.chatBox = aactivity.findViewById(R.id.alyn_chat_box);
        this.chat = aactivity.findViewById(R.id.chat);
        this.bpExtend = aactivity.findViewById(R.id.bp_extend);
        this.bpScroll = aactivity.findViewById(R.id.bp_scroll);
        this.voiceButton = aactivity.findViewById(R.id.alyn_voice_btn);

        loadChatAndHudSettings();
        setupChatList();
        setupButtonPanel();
        setupVoiceButton();
    }

    private void loadChatAndHudSettings() {
        SharedPreferences prefs = activity.getSharedPreferences("samp_settings", Context.MODE_PRIVATE);
        showVoiceButton = prefs.getBoolean("voice_chat", true);
        showTimestamps = prefs.getBoolean("timestamp", false);
        int visibleLines = prefs.getInt("chat_strings", 8);
        float iniFontSize = prefs.getFloat("font_size", 30.0f);

        try {
            File ini = AlyaLiteDownloader.getSettingsFile();
            if (ini.exists()) {
                Wini w = new Wini(ini);
                String voice = w.get("gui", "voicelist");
                if (voice != null) {
                    showVoiceButton = !"0".equals(voice.trim());
                }
                String ts = w.get("gui", "timestamp");
                if (ts != null) {
                    showTimestamps = "1".equals(ts.trim());
                }
                String linesStr = w.get("gui", "ChatMaxMessages");
                if (linesStr != null) {
                    visibleLines = Math.max(5, Math.min(20, Integer.parseInt(linesStr.trim())));
                }
                String fsStr = w.get("gui", "FontSize");
                if (fsStr != null) {
                    iniFontSize = Float.parseFloat(fsStr.trim());
                }
            }
        } catch (Exception ignored) {
        }

        chatTextSizeSp = Math.max(8.0f, Math.min(18.0f, iniFontSize * 0.38f));
        maxChatHistory = Math.max(40, visibleLines * 4);

        if (chatBox != null) {
            int lineHeightPx = NvEventQueueActivity.dpToPx(chatTextSizeSp + 5.0f, activity);
            int targetHeight = Math.max(NvEventQueueActivity.dpToPx(72f, activity),
                    Math.min(NvEventQueueActivity.dpToPx(170f, activity), lineHeightPx * visibleLines + NvEventQueueActivity.dpToPx(10f, activity)));
            ViewGroup.LayoutParams lp = chatBox.getLayoutParams();
            if (lp != null) {
                lp.height = targetHeight;
                chatBox.setLayoutParams(lp);
            }
        }
    }

    private void setupChatList() {
        if (chat == null) {
            return;
        }
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(activity);
        mLayoutManager.setSmoothScrollbarEnabled(true);
        mLayoutManager.setStackFromEnd(true);
        chat.setLayoutManager(mLayoutManager);

        adapter = new ChatAdapter(activity, chat_lines);
        chat.setAdapter(adapter);
        chat.setVisibility(View.VISIBLE);

        final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        chat.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getX() - downX) < touchSlop
                                && Math.abs(event.getY() - downY) < touchSlop) {
                            ShowChatj();
                            return true;
                        }
                        break;
                }
                return false;
            }
        });

        if (chatBox != null) {
            chatBox.setOnClickListener(v -> ShowChatj());
        }
    }

    private void setupButtonPanel() {
        if (bpExtend != null && bpScroll != null) {
            bpExtend.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.button_click));
                panelExtended = !panelExtended;
                bpExtend.setText(panelExtended ? "<<" : ">>");
                bpScroll.setVisibility(panelExtended ? View.VISIBLE : View.GONE);
            });
        }

        bindKeyButton(R.id.bp_esc, 0);
        bindKeyButton(R.id.bp_tab, 1);
        bindKeyButton(R.id.bp_alt, 2);
        bindKeyButton(R.id.bp_spc, 3);
        bindKeyButton(R.id.bp_f, 4);
        bindKeyButton(R.id.bp_h, 5);
        bindKeyButton(R.id.bp_2, 6);
        bindKeyButton(R.id.bp_y, 7);
        bindKeyButton(R.id.bp_n, 8);
        bindKeyButton(R.id.bp_c, 9);
        bindKeyButton(R.id.bp_g, 10);
    }

    private void bindKeyButton(int viewId, int keyId) {
        View btn = activity.findViewById(viewId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.button_click));
                NvEventQueueActivity.getInstance().sendButtonPanelKey(keyId);
            });
        }
    }

    private void setupVoiceButton() {
        if (voiceButton != null) {
            voiceButton.setVisibility(showVoiceButton ? View.VISIBLE : View.GONE);
            voiceButton.setOnClickListener(v -> {
                v.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.button_click));
                boolean recording = NvEventQueueActivity.getInstance().toggleVoiceInput();
                voiceButton.setColorFilter(
                        recording ? Color.parseColor("#66BB6A") : Color.WHITE,
                        PorterDuff.Mode.SRC_IN
                );
            });
        }
    }

    public void UpdateHudInfo(int health, int armour, int hunger, int weaponidweik, int ammo, int playerid, int money, int wanted) {
        // Native GTA:SA HUD renders HP, Armor, Money, Weapon, and Wanted stars cleanly
    }

    public void AddChatMessage(String msg, int textcolor) {
        activity.runOnUiThread(() -> {
            if (adapter == null || msg == null) {
                return;
            }
            int rgb = (textcolor >>> 8) & 0xFFFFFF;
            if (rgb == 0 && (textcolor & 0xFFFFFF) != 0) {
                rgb = textcolor & 0xFFFFFF;
            }
            String hex = String.format("%06X", rgb);
            String prefix = showTimestamps ? ("{AFAFAF}[" + timeFormat.format(new Date()) + "] {" + hex + "}") : ("{" + hex + "}");
            adapter.addItem(prefix + msg);
        });
    }

    public void ShowChatj() {
        NvEventQueueActivity act = NvEventQueueActivity.getInstance();
        if (act == null) {
            return;
        }
        if (!act.isInputShowing()) {
            act.showInputLayout();
            keybo = 1;
        } else {
            act.hideInputLayout();
            keybo = 0;
        }
    }

    public void ShowGps() {
    }

    public void HideGps() {
    }

    public void ShowX2() {
    }

    public void HideX2() {
    }

    public void ShowZona() {
    }

    public void HideZona() {
    }

    public void ShowRadar() {
    }

    public void HideRadar() {
    }

    public void ShowHud() {
        if (hud_layout != null) {
            hud_layout.setVisibility(View.VISIBLE);
        }
    }

    public void HideHud() {
        if (hud_layout != null) {
            hud_layout.setVisibility(View.GONE);
        }
    }

    public void ShowChat() {
        if (chatBox != null) {
            chatBox.setVisibility(View.VISIBLE);
        }
        if (chat != null) {
            chat.setVisibility(View.VISIBLE);
        }
    }

    public void closekey() {
        NvEventQueueActivity act = NvEventQueueActivity.getInstance();
        if (act != null) {
            act.hideInputLayout();
            act.togglePlayer(0);
        }
        keybo = 0;
    }

    public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        private final LayoutInflater inflater;
        private final List<Spanned> items;

        ChatAdapter(Activity context, List<Spanned> items) {
            this.items = items;
            this.inflater = LayoutInflater.from(context);
        }

        @NotNull
        @Override
        public ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.chatline, parent, false);
            view.setOnClickListener(view1 -> ShowChatj());
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NotNull ViewHolder holder, int position) {
            holder.chatLineText.setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSizeSp);
            holder.chatLineText.setText(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public List<Spanned> getItems() {
            return items;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            final TextView chatLineText;

            ViewHolder(View view) {
                super(view);
                chatLineText = view.findViewById(R.id.chat_line_text);
            }
        }

        public void addItem(String item) {
            activity.runOnUiThread(() -> {
                if (this.items.size() > maxChatHistory) {
                    this.items.remove(0);
                    notifyItemRemoved(0);
                }
                items.add(Utils.transfromColors(item));
                notifyItemInserted(this.items.size() - 1);
                if (chat != null && chat.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    chat.smoothScrollToPosition(this.items.size() - 1);
                }
            });
        }
    }
}
