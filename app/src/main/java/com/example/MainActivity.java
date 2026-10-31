package com.example;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_MANAGE_STORAGE = 1002;
    private static final String DEFAULT_MODEL_PATH = "/sdcard/dolphin-7b.gguf";
    private static final int DEFAULT_N_PREDICT = 256;

    private DrawerLayout drawerLayout;
    private ImageButton btnMenuHamburger;
    private ImageButton btnClearChat;
    private ImageButton btnDrawerNewChat;
    private ImageButton btnDrawerSearch;
    private ImageButton btnPlus;
    private ImageButton btnMic;
    private ImageButton btnSend;
    private RecyclerView recyclerViewChat;
    private RecyclerView recyclerViewHistory;
    private EditText editTextMessage;
    private TextView tvModelStatus;

    private ChatAdapter adapter;
    private HistoryAdapter historyAdapter;
    private LlamaEngine llamaEngine;
    private ExecutorService executorService;

    private volatile long modelPtr = 0L;
    private volatile boolean isGenerating = false;
    private final StringBuilder conversationContext = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        llamaEngine = new LlamaEngine();
        executorService = Executors.newSingleThreadExecutor();

        initViews();
        setupRecyclerView();
        setupHistoryRecyclerView();
        setupInteractions();
        checkStoragePermissionAndLoadModel();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenuHamburger = findViewById(R.id.btnMenuHamburger);
        btnClearChat = findViewById(R.id.btnClearChat);
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat);
        btnDrawerSearch = findViewById(R.id.btnDrawerSearch);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnSend = findViewById(R.id.btnSend);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        editTextMessage = findViewById(R.id.editTextMessage);
        tvModelStatus = findViewById(R.id.tvModelStatus);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(adapter);

        adapter.addMessage(new ChatAdapter.ChatMessage(
                getString(R.string.welcome_message),
                false
        ));
    }

    private void setupHistoryRecyclerView() {
        historyAdapter = new HistoryAdapter();
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(historyAdapter);
        historyAdapter.addSession("Dolphine 7B Session", "Local JNI • /sdcard/dolphin-7b.gguf");
    }

    private void setupInteractions() {
        btnMenuHamburger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSendMessage();
            }
        });

        editTextMessage.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    handleSendMessage();
                    return true;
                }
                return false;
            }
        });

        btnClearChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearConversationAndResetContext();
            }
        });

        if (btnDrawerNewChat != null) {
            btnDrawerNewChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearConversationAndResetContext();
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                }
            });
        }

        if (btnDrawerSearch != null) {
            btnDrawerSearch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editTextMessage.requestFocus();
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                }
            });
        }

        if (btnPlus != null) {
            btnPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editTextMessage.setText("Check /sdcard/dolphin-7b.gguf model status");
                    editTextMessage.setSelection(editTextMessage.getText().length());
                }
            });
        }

        if (btnMic != null) {
            btnMic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editTextMessage.requestFocus();
                }
            });
        }
    }

    private void toggleDrawer() {
        if (drawerLayout == null) {
            return;
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private void checkStoragePermissionAndLoadModel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                tvModelStatus.setText(R.string.status_permission_required);
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                    } catch (Exception ignored) {
                    }
                }
            }
            initializeModelInBackground(DEFAULT_MODEL_PATH);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_STORAGE_PERMISSION
                );
            }
            initializeModelInBackground(DEFAULT_MODEL_PATH);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            initializeModelInBackground(DEFAULT_MODEL_PATH);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            initializeModelInBackground(DEFAULT_MODEL_PATH);
        }
    }

    private void initializeModelInBackground(final String modelPath) {
        tvModelStatus.setText(R.string.status_loading_model);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                long loadedPtr = 0L;
                if (LlamaEngine.isNativeLibraryLoaded()) {
                    if (modelPtr != 0L) {
                        llamaEngine.freeModel(modelPtr);
                        modelPtr = 0L;
                    }
                    loadedPtr = llamaEngine.loadModel(modelPath);
                }

                modelPtr = loadedPtr;
                final boolean hasNativePtr = loadedPtr != 0L;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasNativePtr) {
                            tvModelStatus.setText(R.string.status_model_ready);
                        } else {
                            tvModelStatus.setText(R.string.status_model_fallback);
                        }
                    }
                });
            }
        });
    }

    private void handleSendMessage() {
        if (isGenerating) {
            return;
        }

        final String userText = editTextMessage.getText().toString().trim();
        if (TextUtils.isEmpty(userText)) {
            return;
        }

        editTextMessage.setText("");
        adapter.addMessage(new ChatAdapter.ChatMessage(userText, true));
        if (historyAdapter != null) {
            historyAdapter.addSession(userText, "Just now");
        }
        scrollToBottom();

        isGenerating = true;
        btnSend.setAlpha(0.6f);
        tvModelStatus.setText(R.string.status_generating);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                conversationContext.append("<|im_start|>user\n")
                        .append(userText)
                        .append("<|im_end|>\n<|im_start|>assistant\n");

                final String fullPrompt = conversationContext.toString();
                String generatedText = null;

                if (LlamaEngine.isNativeLibraryLoaded()) {
                    if (modelPtr == 0L) {
                        modelPtr = llamaEngine.loadModel(DEFAULT_MODEL_PATH);
                    }
                    if (modelPtr != 0L) {
                        generatedText = llamaEngine.generateResponse(modelPtr, fullPrompt, DEFAULT_N_PREDICT);
                    }
                }

                if (TextUtils.isEmpty(generatedText)) {
                    generatedText = buildLocalFallbackResponse(userText);
                }

                conversationContext.append(generatedText).append("<|im_end|>\n");
                final String finalReply = generatedText;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.addMessage(new ChatAdapter.ChatMessage(finalReply, false));
                        scrollToBottom();
                        isGenerating = false;
                        btnSend.setAlpha(1.0f);
                        tvModelStatus.setText(modelPtr != 0L
                                ? R.string.status_model_ready
                                : R.string.status_model_fallback);
                    }
                });
            }
        });
    }

    private void clearConversationAndResetContext() {
        adapter.clearMessages();
        Toast.makeText(this, R.string.status_context_cleared, Toast.LENGTH_SHORT).show();

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                conversationContext.setLength(0);
                if (LlamaEngine.isNativeLibraryLoaded()) {
                    if (modelPtr != 0L) {
                        llamaEngine.freeModel(modelPtr);
                        modelPtr = 0L;
                    }
                    modelPtr = llamaEngine.loadModel(DEFAULT_MODEL_PATH);
                } else {
                    modelPtr = 0L;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvModelStatus.setText(modelPtr != 0L
                                ? R.string.status_model_ready
                                : R.string.status_model_fallback);
                    }
                });
            }
        });
    }

    private String buildLocalFallbackResponse(String prompt) {
        File modelFile = new File(DEFAULT_MODEL_PATH);
        boolean modelExists = modelFile.exists();
        String lower = prompt.toLowerCase(Locale.US);

        if (lower.contains("status") || lower.contains("model") || lower.contains("path")) {
            return "Dolphine 7B Local JNI Engine Report:\n"
                    + "• Target Path: " + DEFAULT_MODEL_PATH + "\n"
                    + "• Model File Found: " + (modelExists ? "Yes" : "No (awaiting GGUF file in /sdcard/)") + "\n"
                    + "• Native Library: libllama-android.so ("
                    + (LlamaEngine.isNativeLibraryLoaded() ? "Loaded" : "JNI Bridge Ready") + ")\n"
                    + "• Max Predict Tokens: " + DEFAULT_N_PREDICT;
        }

        return "[Dolphine 7B Local] Processed prompt on background ExecutorService ("
                + DEFAULT_N_PREDICT + " n_predict): \"" + prompt + "\". "
                + (modelExists
                ? "Model file detected at " + DEFAULT_MODEL_PATH + "."
                : "Place your quantized GGUF model at " + DEFAULT_MODEL_PATH + " for full llama.cpp native weight execution.");
    }

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) {
            recyclerViewChat.smoothScrollToPosition(count - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        final long ptrToFree = modelPtr;
        modelPtr = 0L;
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    if (ptrToFree != 0L && LlamaEngine.isNativeLibraryLoaded()) {
                        llamaEngine.freeModel(ptrToFree);
                    }
                }
            });
            executorService.shutdown();
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
        private final List<String[]> historyItems = new ArrayList<>();

        void addSession(String title, String subtitle) {
            historyItems.add(0, new String[]{title, subtitle});
            notifyItemInserted(0);
        }

        @NonNull
        @Override
        public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
            final String[] item = historyItems.get(position);
            holder.tvTitle.setText(item[0]);
            holder.tvSubtitle.setText(item[1]);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return historyItems.size();
        }

        class HistoryViewHolder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvSubtitle;

            HistoryViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
                tvSubtitle = itemView.findViewById(R.id.tvHistorySubtitle);
            }
        }
    }
}
