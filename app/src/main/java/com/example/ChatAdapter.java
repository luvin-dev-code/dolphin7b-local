package com.example;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public static class ChatMessage {
        private final String content;
        private final boolean isUser;
        private final String timestamp;

        public ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        }

        public String getContent() {
            return content;
        }

        public boolean isUser() {
            return isUser;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clearMessages() {
        int size = messages.size();
        if (size > 0) {
            messages.clear();
            notifyItemRangeRemoved(0, size);
        } else {
            notifyDataSetChanged();
        }
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout bubbleContainer;
        private final TextView tvSenderRole;
        private final TextView tvTimestamp;
        private final TextView tvMessageContent;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            tvSenderRole = itemView.findViewById(R.id.tvSenderRole);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
        }

        void bind(ChatMessage message) {
            tvMessageContent.setText(message.getContent());
            tvTimestamp.setText(message.getTimestamp());

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bubbleContainer.getLayoutParams();
            if (message.isUser()) {
                params.gravity = Gravity.END;
                bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_user);
                tvSenderRole.setText(R.string.role_user);
                tvSenderRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent_orange));
            } else {
                params.gravity = Gravity.START;
                bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_ai);
                tvSenderRole.setText(R.string.role_assistant);
                tvSenderRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent_orange));
            }
            bubbleContainer.setLayoutParams(params);
        }
    }
}
