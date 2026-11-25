package com.ryzn.game.ui.widgets;

import android.app.Activity;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ryzn.game.R;
import com.ryzn.game.gui.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class Chat {
    private boolean visible;
    private final List<SpannableString> messageList;
    private final ChatAdapter chatAdapter;
    private final RelativeLayout chatLayout;
    private final RecyclerView recyclerView;
    private final Listener listener;

    public interface Listener {
        void chatShowKeyboard(boolean z);
        void _chatSend(String str);
    }

    public Chat(Activity activity, Listener listener) {
        this.listener = listener;
        this.visible = false;
        this.messageList = new ArrayList<>();
        this.chatAdapter = new ChatAdapter(messageList);

        chatLayout = (RelativeLayout) activity.getLayoutInflater().inflate(R.layout.activity_game_chat, null);
        activity.addContentView(chatLayout, new RelativeLayout.LayoutParams(-1, -1));

        LinearLayout chat = chatLayout.findViewById(R.id.chat);
        chat.setOnClickListener(v -> listener.chatShowKeyboard(true));

        recyclerView = chatLayout.findViewById(R.id.recyclerview_chat);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(chatAdapter);

        show(false);
    }

    public boolean isVisible() {
        return visible;
    }

    public void show(boolean show) {
        visible = show;
        chatLayout.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void onSend(String keyboardString) {
        if (!keyboardString.isEmpty()) {
            listener._chatSend(keyboardString);
            listener.chatShowKeyboard(false);
        }
    }

    public void addMessage(SpannableString message) {
        messageList.add(message);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.smoothScrollToPosition(messageList.size() - 1);
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {
        private final List<SpannableString> messageList;

        public ChatAdapter(List<SpannableString> messageList) {
            this.messageList = messageList;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_chat_item, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            holder.bind(messageList.get(position));
        }

        @Override
        public int getItemCount() {
            return messageList.size();
        }

        public static class MessageViewHolder extends RecyclerView.ViewHolder {
            private final TextView messageTextView;

            public MessageViewHolder(View itemView) {
                super(itemView);
                messageTextView = itemView.findViewById(R.id.textview_message);
            }

            public void bind(SpannableString message) {
                messageTextView.setText(Utils.getColoredString(message.toString()));
            }
        }
    }
}
