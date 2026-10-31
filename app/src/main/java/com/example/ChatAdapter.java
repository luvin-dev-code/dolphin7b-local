package com.example;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

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
        private final boolean isTyping;
        private final String timestamp;

        public ChatMessage(String content, boolean isUser) {
            this(content, isUser, false);
        }

        public ChatMessage(String content, boolean isUser, boolean isTyping) {
            this(content, isUser, isTyping, new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
        }

        public ChatMessage(String content, boolean isUser, String timestamp) {
            this(content, isUser, false, timestamp);
        }

        public ChatMessage(String content, boolean isUser, boolean isTyping, String timestamp) {
            this.content = content;
            this.isUser = isUser;
            this.isTyping = isTyping;
            this.timestamp = timestamp != null
                    ? timestamp
                    : new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        }

        public String getContent() {
            return content;
        }

        public boolean isUser() {
            return isUser;
        }

        public boolean isTyping() {
            return isTyping;
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

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void showTypingIndicator() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isTyping()) {
                return;
            }
        }
        messages.add(new ChatMessage("", false, true));
        notifyItemInserted(messages.size() - 1);
    }

    public void hideTypingIndicator() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isTyping()) {
                messages.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
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
    public void onViewRecycled(@NonNull ChatViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stopTypingAnimation();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private static final int MENU_ID_COPY = 1;

        private final LinearLayout bubbleContainer;
        private final TextView tvSenderRole;
        private final TextView tvTimestamp;
        private final TextView tvMessageContent;
        private final LinearLayout typingIndicatorContainer;
        private final View typingDot1;
        private final View typingDot2;
        private final View typingDot3;
        private AnimatorSet typingAnimatorSet;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleContainer = itemView.findViewById(R.id.bubbleContainer);
            tvSenderRole = itemView.findViewById(R.id.tvSenderRole);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvMessageContent = itemView.findViewById(R.id.tvMessageContent);
            typingIndicatorContainer = itemView.findViewById(R.id.typingIndicatorContainer);
            typingDot1 = itemView.findViewById(R.id.typingDot1);
            typingDot2 = itemView.findViewById(R.id.typingDot2);
            typingDot3 = itemView.findViewById(R.id.typingDot3);
        }

        void bind(final ChatMessage message) {
            tvTimestamp.setText(message.getTimestamp());

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bubbleContainer.getLayoutParams();
            if (message.isUser()) {
                params.gravity = Gravity.END;
                bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_user);
                tvSenderRole.setText(R.string.role_user);
                tvSenderRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent_purple_light));
            } else {
                params.gravity = Gravity.START;
                bubbleContainer.setBackgroundResource(R.drawable.bg_bubble_ai);
                tvSenderRole.setText(R.string.role_assistant);
                tvSenderRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.accent_orange));
            }
            bubbleContainer.setLayoutParams(params);

            if (message.isTyping()) {
                tvMessageContent.setVisibility(View.GONE);
                typingIndicatorContainer.setVisibility(View.VISIBLE);
                startTypingAnimation();

                bubbleContainer.setOnLongClickListener(null);
                tvMessageContent.setOnLongClickListener(null);
                itemView.setOnLongClickListener(null);
            } else {
                stopTypingAnimation();
                typingIndicatorContainer.setVisibility(View.GONE);
                tvMessageContent.setVisibility(View.VISIBLE);
                tvMessageContent.setText(message.getContent());

                View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View anchorView) {
                        showCopyContextMenu(anchorView, message.getContent());
                        return true;
                    }
                };

                bubbleContainer.setOnLongClickListener(longClickListener);
                tvMessageContent.setOnLongClickListener(longClickListener);
                itemView.setOnLongClickListener(longClickListener);
            }
        }

        private void startTypingAnimation() {
            stopTypingAnimation();
            float bounceOffset = -8f * itemView.getResources().getDisplayMetrics().density;

            Animator anim1 = createDotAnimator(typingDot1, 0L, bounceOffset);
            Animator anim2 = createDotAnimator(typingDot2, 150L, bounceOffset);
            Animator anim3 = createDotAnimator(typingDot3, 300L, bounceOffset);

            typingAnimatorSet = new AnimatorSet();
            typingAnimatorSet.playTogether(anim1, anim2, anim3);
            typingAnimatorSet.start();
        }

        private Animator createDotAnimator(View dot, long startDelay, float bounceOffset) {
            ObjectAnimator translateY = ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, bounceOffset, 0f);
            translateY.setDuration(600L);
            translateY.setStartDelay(startDelay);
            translateY.setRepeatCount(ValueAnimator.INFINITE);
            translateY.setInterpolator(new AccelerateDecelerateInterpolator());

            ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.35f, 1.0f, 0.35f);
            alpha.setDuration(600L);
            alpha.setStartDelay(startDelay);
            alpha.setRepeatCount(ValueAnimator.INFINITE);
            alpha.setInterpolator(new AccelerateDecelerateInterpolator());

            AnimatorSet dotSet = new AnimatorSet();
            dotSet.playTogether(translateY, alpha);
            return dotSet;
        }

        void stopTypingAnimation() {
            if (typingAnimatorSet != null) {
                typingAnimatorSet.cancel();
                typingAnimatorSet = null;
            }
            if (typingDot1 != null) {
                typingDot1.setTranslationY(0f);
                typingDot1.setAlpha(1f);
            }
            if (typingDot2 != null) {
                typingDot2.setTranslationY(0f);
                typingDot2.setAlpha(1f);
            }
            if (typingDot3 != null) {
                typingDot3.setTranslationY(0f);
                typingDot3.setAlpha(1f);
            }
        }

        private void showCopyContextMenu(View anchorView, final String textToCopy) {
            final Context context = anchorView.getContext();
            PopupMenu popupMenu = new PopupMenu(context, anchorView);
            popupMenu.getMenu().add(Menu.NONE, MENU_ID_COPY, Menu.NONE, R.string.menu_copy_text);
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == MENU_ID_COPY) {
                        copyTextToClipboard(context, textToCopy);
                        return true;
                    }
                    return false;
                }
            });
            popupMenu.show();
        }

        private void copyTextToClipboard(Context context, String text) {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
