package com.byparad1st.game.gui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.text.Spanned;
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
import com.nvidia.devtech.NvEventQueueActivity;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

    public HudManager(Activity aactivity) {
        this.activity = aactivity;
        this.hud_layout = aactivity.findViewById(R.id.hud_main);
        this.chatBox = aactivity.findViewById(R.id.alyn_chat_box);
        this.chat = aactivity.findViewById(R.id.chat);
        this.bpExtend = aactivity.findViewById(R.id.bp_extend);
        this.bpScroll = aactivity.findViewById(R.id.bp_scroll);
        this.voiceButton = aactivity.findViewById(R.id.alyn_voice_btn);

        setupChatList();
        setupButtonPanel();
        setupVoiceButton();
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

        // Allow tapping anywhere inside chat box (even empty area) to open chat keyboard
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
            adapter.addItem("{" + hex + "}" + msg);
        });
    }

    public void ShowChatj() {
        NvEventQueueActivity act = NvEventQueueActivity.getInstance();
        if (act == null) {
            return;
        }
        if (keybo == 0) {
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
        NvEventQueueActivity.getInstance().togglePlayer(0);
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
                if (this.items.size() > 60) {
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
