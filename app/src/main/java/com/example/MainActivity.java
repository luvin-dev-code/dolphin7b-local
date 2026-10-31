package com.example;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.system.Os;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
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

    private static final String PREFS_NAME = "dolphine_inference_prefs";
    private static final String KEY_THREAD_COUNT = "thread_count";
    private static final String KEY_CONTEXT_WINDOW = "context_window";
    private static final String KEY_MAX_PREDICT = "max_predict";
    private static final String KEY_KEEP_HISTORY = "keep_history";

    private DrawerLayout drawerLayout;
    private ImageButton btnMenuHamburger;
    private ImageButton btnClearChat;
    private ImageButton btnDrawerNewChat;
    private ImageButton btnDrawerSearch;
    private EditText editDrawerSearch;
    private TextView tvEmptyHistory;
    private LinearLayout btnDrawerSettings;
    private TextView tvDrawerSettingsSummary;
    private ImageButton btnPlus;
    private ImageButton btnMic;
    private ImageButton btnSend;
    private RecyclerView recyclerViewChat;
    private LinearLayout emptyChatWelcomeContainer;
    private RecyclerView recyclerViewHistory;
    private EditText editTextMessage;
    private TextView tvModelStatus;

    private ChatAdapter adapter;
    private SessionHistoryAdapter historyAdapter;
    private ChatSessionDatabaseHelper dbHelper;
    private LlamaEngine llamaEngine;
    private ExecutorService executorService;

    private volatile long modelPtr = 0L;
    private volatile boolean isGenerating = false;
    private volatile long currentSessionId = -1L;
    private final List<String> conversationTurns = new ArrayList<>();

    private int configuredThreads = 4;
    private int configuredContextWindow = 512;
    private int configuredMaxPredict = 64;
    private boolean configuredKeepHistory = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executorService = Executors.newSingleThreadExecutor();
        dbHelper = new ChatSessionDatabaseHelper(this);

        loadInferenceSettings();
        applyNativeThreadEnvironment(configuredThreads);

        initViews();
        setupRecyclerView();
        setupHistoryRecyclerView();
        setupInteractions();
        updateSettingsSummaryBadges();
        refreshSessionHistoryList();
        requestStoragePermissionOrInitModel();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        btnMenuHamburger = findViewById(R.id.btnMenuHamburger);
        btnClearChat = findViewById(R.id.btnClearChat);
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat);
        btnDrawerSearch = findViewById(R.id.btnDrawerSearch);
        editDrawerSearch = findViewById(R.id.editDrawerSearch);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        btnDrawerSettings = findViewById(R.id.btnDrawerSettings);
        tvDrawerSettingsSummary = findViewById(R.id.tvDrawerSettingsSummary);
        btnPlus = findViewById(R.id.btnPlus);
        btnMic = findViewById(R.id.btnMic);
        btnSend = findViewById(R.id.btnSend);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        emptyChatWelcomeContainer = findViewById(R.id.emptyChatWelcomeContainer);
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        editTextMessage = findViewById(R.id.editTextMessage);
        tvModelStatus = findViewById(R.id.tvModelStatus);
    }

    private void loadInferenceSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int maxCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int defaultThreads = Math.min(4, maxCores);

        configuredThreads = prefs.getInt(KEY_THREAD_COUNT, defaultThreads);
        if (configuredThreads < 1) {
            configuredThreads = 1;
        } else if (configuredThreads > maxCores) {
            configuredThreads = maxCores;
        }

        configuredContextWindow = Math.max(128, Math.min(4096, prefs.getInt(KEY_CONTEXT_WINDOW, 512)));
        configuredMaxPredict = Math.max(16, Math.min(512, prefs.getInt(KEY_MAX_PREDICT, 64)));
        configuredKeepHistory = prefs.getBoolean(KEY_KEEP_HISTORY, true);
    }

    private void saveInferenceSettings(int threads, int contextWindow, int maxPredict, boolean keepHistory) {
        configuredThreads = threads;
        configuredContextWindow = contextWindow;
        configuredMaxPredict = maxPredict;
        configuredKeepHistory = keepHistory;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_THREAD_COUNT, threads)
                .putInt(KEY_CONTEXT_WINDOW, contextWindow)
                .putInt(KEY_MAX_PREDICT, maxPredict)
                .putBoolean(KEY_KEEP_HISTORY, keepHistory)
                .apply();

        applyNativeThreadEnvironment(threads);
        updateSettingsSummaryBadges();
    }

    private void applyNativeThreadEnvironment(int threads) {
        try {
            String value = String.valueOf(Math.max(1, threads));
            Os.setenv("OMP_NUM_THREADS", value, true);
            Os.setenv("GGML_NUM_THREADS", value, true);
        } catch (Throwable ignored) {
        }
    }

    private void updateSettingsSummaryBadges() {
        String summary = String.format(
                Locale.US,
                "%d Threads • %d Ctx • %d Tok",
                configuredThreads,
                configuredContextWindow,
                configuredMaxPredict
        );
        if (tvDrawerSettingsSummary != null) {
            tvDrawerSettingsSummary.setText(summary);
        }
        if (tvModelStatus != null && !isGenerating) {
            if (!hasStoragePermission()) {
                tvModelStatus.setText(R.string.status_permission_required);
            } else if (modelPtr != 0L) {
                tvModelStatus.setText("Ready • " + summary);
            } else {
                tvModelStatus.setText("JNI Active • " + summary);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(adapter);
        updateEmptyChatState();
    }

    private void updateEmptyChatState() {
        if (emptyChatWelcomeContainer == null || recyclerViewChat == null || adapter == null) {
            return;
        }
        boolean isChatEmpty = adapter.getItemCount() == 0;
        emptyChatWelcomeContainer.setVisibility(isChatEmpty ? View.VISIBLE : View.GONE);
        recyclerViewChat.setVisibility(isChatEmpty ? View.GONE : View.VISIBLE);
    }

    private void setupHistoryRecyclerView() {
        historyAdapter = new SessionHistoryAdapter();
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(historyAdapter);
    }

    private void refreshSessionHistoryList() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final List<ChatSessionDatabaseHelper.ChatSession> sessions = dbHelper.getAllSessions();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        historyAdapter.setSessions(sessions);
                        updateEmptyHistoryState();
                    }
                });
            }
        });
    }

    private void updateEmptyHistoryState() {
        if (tvEmptyHistory == null || recyclerViewHistory == null) {
            return;
        }
        boolean isEmpty = historyAdapter.getItemCount() == 0;
        tvEmptyHistory.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerViewHistory.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void setupInteractions() {
        btnMenuHamburger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer();
            }
        });

        if (btnDrawerSettings != null) {
            btnDrawerSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                    showInferenceSettingsDialog();
                }
            });
        }

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
                startNewChatSession(true);
            }
        });

        if (btnDrawerNewChat != null) {
            btnDrawerNewChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startNewChatSession(true);
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
                    if (editDrawerSearch != null) {
                        if (editDrawerSearch.getVisibility() == View.VISIBLE) {
                            editDrawerSearch.setText("");
                            editDrawerSearch.setVisibility(View.GONE);
                        } else {
                            editDrawerSearch.setVisibility(View.VISIBLE);
                            editDrawerSearch.requestFocus();
                        }
                    }
                }
            });
        }

        if (editDrawerSearch != null) {
            editDrawerSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (historyAdapter != null) {
                        historyAdapter.filter(s != null ? s.toString() : "");
                        updateEmptyHistoryState();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
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

    private void showInferenceSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_inference_settings);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94f);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        final TextView tvRamUsageStats = dialog.findViewById(R.id.tvRamUsageStats);
        final ProgressBar progressDeviceRam = dialog.findViewById(R.id.progressDeviceRam);
        final TextView tvRamRecommendation = dialog.findViewById(R.id.tvRamRecommendation);

        final TextView tvThreadCountValue = dialog.findViewById(R.id.tvThreadCountValue);
        final SeekBar seekBarThreads = dialog.findViewById(R.id.seekBarThreads);

        final TextView tvContextWindowValue = dialog.findViewById(R.id.tvContextWindowValue);
        final TextView tvContextKvEstimate = dialog.findViewById(R.id.tvContextKvEstimate);
        final SeekBar seekBarContextWindow = dialog.findViewById(R.id.seekBarContextWindow);

        final TextView tvPredictTokensValue = dialog.findViewById(R.id.tvPredictTokensValue);
        final SeekBar seekBarPredictTokens = dialog.findViewById(R.id.seekBarPredictTokens);

        final Switch switchKeepHistory = dialog.findViewById(R.id.switchKeepHistory);
        final TextView btnCancelSettings = dialog.findViewById(R.id.btnCancelSettings);
        final TextView btnSaveSettings = dialog.findViewById(R.id.btnSaveSettings);

        final int maxCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        seekBarThreads.setMax(Math.max(0, maxCores - 1));

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }

        final double totalRamGb = memoryInfo.totalMem > 0
                ? (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
                : 6.0;
        final double availRamGb = memoryInfo.availMem > 0
                ? (memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0))
                : 2.5;
        final double usedRamGb = Math.max(0.1, totalRamGb - availRamGb);
        final int usedPercent = (int) Math.min(100, Math.max(5, Math.round((usedRamGb / totalRamGb) * 100.0)));

        progressDeviceRam.setProgress(usedPercent);
        tvRamUsageStats.setText(String.format(
                Locale.US,
                "%.1f GB Available / %.1f GB (%d Cores)",
                availRamGb,
                totalRamGb,
                maxCores
        ));

        tvRamRecommendation.setText(String.format(
                Locale.US,
                "RAM Used: %d%% • Freely adjust CPU threads (1–%d), context window (128–4096), and max reply tokens (16–512).",
                usedPercent,
                maxCores
        ));

        seekBarThreads.setProgress(Math.min(maxCores - 1, Math.max(0, configuredThreads - 1)));
        seekBarContextWindow.setMax(62);
        int ctxProgress = Math.max(0, Math.min(62, (configuredContextWindow - 128) / 64));
        seekBarContextWindow.setProgress(ctxProgress);

        seekBarPredictTokens.setMax(31);
        int predictProgress = Math.max(0, Math.min(31, (configuredMaxPredict - 16) / 16));
        seekBarPredictTokens.setProgress(predictProgress);

        switchKeepHistory.setChecked(configuredKeepHistory);

        final Runnable updateLabels = new Runnable() {
            @Override
            public void run() {
                int selectedThreads = seekBarThreads.getProgress() + 1;
                int selectedContext = 128 + (seekBarContextWindow.getProgress() * 64);
                int selectedPredict = 16 + (seekBarPredictTokens.getProgress() * 16);

                tvThreadCountValue.setText(selectedThreads + (selectedThreads == 1 ? " Thread" : " Threads"));
                tvContextWindowValue.setText(selectedContext + " Tokens");

                int estimatedKvMb = Math.max(16, selectedContext / 4);
                tvContextKvEstimate.setText(String.format(
                        Locale.US,
                        "Est. KV-Cache RAM: ~%d MB • Context capacity: %d tokens",
                        estimatedKvMb,
                        selectedContext
                ));

                tvPredictTokensValue.setText(selectedPredict + " Tokens");
            }
        };
        updateLabels.run();

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateLabels.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        seekBarThreads.setOnSeekBarChangeListener(seekListener);
        seekBarContextWindow.setOnSeekBarChangeListener(seekListener);
        seekBarPredictTokens.setOnSeekBarChangeListener(seekListener);

        btnCancelSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnSaveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedThreads = seekBarThreads.getProgress() + 1;
                int selectedContext = 128 + (seekBarContextWindow.getProgress() * 64);
                int selectedPredict = 16 + (seekBarPredictTokens.getProgress() * 16);
                boolean keepHistory = switchKeepHistory.isChecked();

                saveInferenceSettings(selectedThreads, selectedContext, selectedPredict, keepHistory);
                Toast.makeText(
                        MainActivity.this,
                        getString(R.string.toast_settings_saved) + " (" + selectedThreads + "T • " + selectedContext + " Ctx • " + selectedPredict + " Tok)",
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showDeleteSessionConfirmDialog(final ChatSessionDatabaseHelper.ChatSession session) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_delete);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView tvDeleteDialogMessage = dialog.findViewById(R.id.tvDeleteDialogMessage);
        TextView btnCancelDelete = dialog.findViewById(R.id.btnCancelDelete);
        TextView btnConfirmDelete = dialog.findViewById(R.id.btnConfirmDelete);

        if (tvDeleteDialogMessage != null) {
            tvDeleteDialogMessage.setText(
                    "Are you sure you want to permanently delete \"" + session.getTitle()
                            + "\" and all of its conversation history?"
            );
        }

        btnCancelDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnConfirmDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                deleteSessionPermanently(session.getId());
            }
        });

        dialog.show();
    }

    private void deleteSessionPermanently(final long sessionIdToDelete) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                dbHelper.deleteSession(sessionIdToDelete);
                final List<ChatSessionDatabaseHelper.ChatSession> updatedSessions = dbHelper.getAllSessions();
                final boolean wasActiveSession = (currentSessionId == sessionIdToDelete);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (wasActiveSession) {
                            startNewChatSession(false);
                        }
                        historyAdapter.setSessions(updatedSessions);
                        updateEmptyHistoryState();
                        Toast.makeText(MainActivity.this, R.string.toast_session_deleted, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void loadSessionIntoChat(final ChatSessionDatabaseHelper.ChatSession session) {
        if (isGenerating) {
            return;
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final List<ChatAdapter.ChatMessage> savedMessages = dbHelper.getMessagesForSession(session.getId());
                final List<String> restoredTurns = new ArrayList<>();

                StringBuilder pairBuilder = new StringBuilder();
                for (int i = 0; i < savedMessages.size(); i++) {
                    ChatAdapter.ChatMessage msg = savedMessages.get(i);
                    if (msg.isUser()) {
                        pairBuilder.setLength(0);
                        pairBuilder.append("<|im_start|>user\n")
                                .append(msg.getContent())
                                .append("<|im_end|>\n");
                    } else {
                        if (pairBuilder.length() > 0) {
                            pairBuilder.append("<|im_start|>assistant\n")
                                    .append(msg.getContent())
                                    .append("<|im_end|>\n");
                            restoredTurns.add(pairBuilder.toString());
                            pairBuilder.setLength(0);
                        }
                    }
                }

                currentSessionId = session.getId();
                synchronized (conversationTurns) {
                    conversationTurns.clear();
                    conversationTurns.addAll(restoredTurns);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        adapter.setMessages(savedMessages);
                        updateEmptyChatState();
                        historyAdapter.notifyDataSetChanged();
                        scrollToBottom();
                    }
                });
            }
        });
    }

    private void toggleDrawer() {
        if (drawerLayout == null) {
            return;
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            refreshSessionHistoryList();
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermissionOrInitModel() {
        if (hasStoragePermission()) {
            initJniAndLoadModel(DEFAULT_MODEL_PATH);
            return;
        }

        tvModelStatus.setText(R.string.status_permission_required);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQUEST_STORAGE_PERMISSION
            );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (hasStoragePermission()) {
                initJniAndLoadModel(DEFAULT_MODEL_PATH);
            } else {
                tvModelStatus.setText(R.string.status_permission_required);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (hasStoragePermission()) {
                initJniAndLoadModel(DEFAULT_MODEL_PATH);
            } else {
                tvModelStatus.setText(R.string.status_permission_required);
            }
        }
    }

    private void initJniAndLoadModel(final String modelPath) {
        if (llamaEngine == null) {
            llamaEngine = new LlamaEngine();
        }

        tvModelStatus.setText(R.string.status_loading_model);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                long loadedPtr = 0L;
                try {
                    applyNativeThreadEnvironment(configuredThreads);
                    File modelFile = new File(modelPath);
                    if (modelFile.exists() && modelFile.isFile() && modelFile.canRead()
                            && LlamaEngine.isNativeLibraryLoaded()) {
                        if (modelPtr != 0L) {
                            llamaEngine.freeModel(modelPtr);
                            modelPtr = 0L;
                        }
                        loadedPtr = llamaEngine.loadModel(modelFile.getAbsolutePath());
                    }
                } catch (Throwable ignored) {
                    loadedPtr = 0L;
                }

                modelPtr = loadedPtr;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        updateSettingsSummaryBadges();
                    }
                });
            }
        });
    }

    private String buildWindowedPrompt(String currentUserText, int contextWindowTokens, int maxPredictTokens, boolean includeHistory) {
        String currentTurnPrompt = "<|im_start|>user\n"
                + currentUserText
                + "<|im_end|>\n<|im_start|>assistant\n";

        if (!includeHistory) {
            return currentTurnPrompt;
        }

        int availablePromptTokens = Math.max(48, contextWindowTokens - maxPredictTokens - 64);
        int maxPromptChars = availablePromptTokens * 4;

        StringBuilder historyBuilder = new StringBuilder();
        synchronized (conversationTurns) {
            for (int i = conversationTurns.size() - 1; i >= 0; i--) {
                String turn = conversationTurns.get(i);
                if (historyBuilder.length() + turn.length() + currentTurnPrompt.length() > maxPromptChars) {
                    break;
                }
                historyBuilder.insert(0, turn);
            }
        }
        historyBuilder.append(currentTurnPrompt);
        return historyBuilder.toString();
    }

    private void handleSendMessage() {
        if (isGenerating) {
            return;
        }

        final String userText = editTextMessage.getText().toString().trim();
        if (TextUtils.isEmpty(userText)) {
            return;
        }

        if (!hasStoragePermission()) {
            requestStoragePermissionOrInitModel();
            return;
        }

        if (llamaEngine == null) {
            llamaEngine = new LlamaEngine();
        }

        editTextMessage.setText("");
        final ChatAdapter.ChatMessage userMsgObj = new ChatAdapter.ChatMessage(userText, true);
        adapter.addMessage(userMsgObj);
        adapter.showTypingIndicator();
        updateEmptyChatState();
        scrollToBottom();

        final int activeThreads = configuredThreads;
        final int activeContextWindow = configuredContextWindow;
        final int activeMaxPredict = Math.min(configuredMaxPredict, Math.max(16, activeContextWindow - 64));
        final boolean activeKeepHistory = configuredKeepHistory;
        final long promptTimestampMillis = System.currentTimeMillis();

        isGenerating = true;
        btnSend.setEnabled(false);
        btnSend.setAlpha(0.6f);
        tvModelStatus.setText(String.format(
                Locale.US,
                "Generating (%dT • %d Ctx • max %d tok)...",
                activeThreads,
                activeContextWindow,
                activeMaxPredict
        ));

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                long sessionId = currentSessionId;
                if (sessionId <= 0L) {
                    sessionId = dbHelper.createSession(userText, promptTimestampMillis);
                    currentSessionId = sessionId;
                } else {
                    dbHelper.updateSessionLastPrompt(sessionId, promptTimestampMillis);
                }

                dbHelper.insertMessage(
                        sessionId,
                        true,
                        userText,
                        userMsgObj.getTimestamp(),
                        promptTimestampMillis
                );

                applyNativeThreadEnvironment(activeThreads);
                final String fullPrompt = buildWindowedPrompt(
                        userText,
                        activeContextWindow,
                        activeMaxPredict,
                        activeKeepHistory
                );
                String generatedText = null;

                try {
                    File modelFile = new File(DEFAULT_MODEL_PATH);
                    if (LlamaEngine.isNativeLibraryLoaded()
                            && modelFile.exists() && modelFile.isFile() && modelFile.canRead()) {
                        if (modelPtr == 0L) {
                            modelPtr = llamaEngine.loadModel(modelFile.getAbsolutePath());
                        }
                        if (modelPtr != 0L) {
                            generatedText = llamaEngine.generateResponse(modelPtr, fullPrompt, activeMaxPredict);
                        }
                    }
                } catch (Throwable ignored) {
                    generatedText = null;
                }

                if (generatedText != null) {
                    generatedText = normalizeNativeOutputToEnglish(
                            generatedText.replace("<|im_end|>", "").trim()
                    );
                }

                if (TextUtils.isEmpty(generatedText)) {
                    generatedText = buildLocalFallbackResponse(userText);
                }

                String completedTurn = "<|im_start|>user\n"
                        + userText
                        + "<|im_end|>\n<|im_start|>assistant\n"
                        + generatedText
                        + "<|im_end|>\n";
                synchronized (conversationTurns) {
                    conversationTurns.add(completedTurn);
                    if (conversationTurns.size() > 20) {
                        conversationTurns.remove(0);
                    }
                }

                final ChatAdapter.ChatMessage aiMsgObj = new ChatAdapter.ChatMessage(generatedText, false);
                dbHelper.insertMessage(
                        sessionId,
                        false,
                        generatedText,
                        aiMsgObj.getTimestamp(),
                        System.currentTimeMillis()
                );

                final List<ChatSessionDatabaseHelper.ChatSession> updatedSessions = dbHelper.getAllSessions();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        adapter.hideTypingIndicator();
                        adapter.addMessage(aiMsgObj);
                        updateEmptyChatState();
                        historyAdapter.setSessions(updatedSessions);
                        updateEmptyHistoryState();
                        scrollToBottom();
                        isGenerating = false;
                        btnSend.setEnabled(true);
                        btnSend.setAlpha(1.0f);
                        updateSettingsSummaryBadges();
                    }
                });
            }
        });
    }

    private void startNewChatSession(boolean showToast) {
        currentSessionId = -1L;
        adapter.clearMessages();
        updateEmptyChatState();
        synchronized (conversationTurns) {
            conversationTurns.clear();
        }
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
        isGenerating = false;
        btnSend.setEnabled(true);
        btnSend.setAlpha(1.0f);
        if (showToast) {
            Toast.makeText(this, R.string.status_context_cleared, Toast.LENGTH_SHORT).show();
        }

        if (!hasStoragePermission()) {
            tvModelStatus.setText(R.string.status_permission_required);
            return;
        }

        if (modelPtr != 0L) {
            updateSettingsSummaryBadges();
        } else {
            initJniAndLoadModel(DEFAULT_MODEL_PATH);
        }
    }

    private String normalizeNativeOutputToEnglish(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replace("Error: Model tidak termuat.", "Error: Model is not loaded.")
                .replace("Error: Tokenisasi gagal.", "Error: Tokenization failed.")
                .replace("Error: Tokenisasi prompt gagal.", "Error: Prompt tokenization failed.")
                .replace("Error: Gagal membuat context.", "Error: Failed to create inference context.")
                .replace("Error: Decode prompt gagal.", "Error: Failed to decode prompt.");
    }

    private String buildLocalFallbackResponse(String prompt) {
        File modelFile = new File(DEFAULT_MODEL_PATH);
        boolean modelExists = modelFile.exists() && modelFile.isFile();
        String lower = prompt.toLowerCase(Locale.US);

        if (lower.contains("status") || lower.contains("model") || lower.contains("path")) {
            String errorDetail = LlamaEngine.getLoadError();
            return "Dolphine 7B Local JNI Engine Report:\n"
                    + "• Target Path: " + DEFAULT_MODEL_PATH + "\n"
                    + "• Model File Found: " + (modelExists ? "Yes" : "No (awaiting GGUF file in /sdcard/)") + "\n"
                    + "• Native Library: libllama-android.so ("
                    + (LlamaEngine.isNativeLibraryLoaded() ? "Loaded" : "Not Loaded") + ")\n"
                    + (errorDetail != null ? "• JNI Load Error: " + errorDetail + "\n" : "")
                    + "• Active Threads: " + configuredThreads + "\n"
                    + "• Context Window: " + configuredContextWindow + " tokens\n"
                    + "• Max Predict Tokens: " + configuredMaxPredict;
        }

        return "[Dolphine 7B Local] Processed prompt on background ExecutorService ("
                + configuredThreads + " threads, " + configuredContextWindow + " ctx, "
                + configuredMaxPredict + " n_predict): \"" + prompt + "\". "
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
        if (executorService != null && !executorService.isShutdown()) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    long ptr = modelPtr;
                    modelPtr = 0L;
                    if (ptr != 0L && llamaEngine != null && LlamaEngine.isNativeLibraryLoaded()) {
                        try {
                            llamaEngine.freeModel(ptr);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (dbHelper != null) {
                        try {
                            dbHelper.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
            executorService.shutdown();
        }
        super.onDestroy();
    }

    private class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.SessionViewHolder> {
        private final List<ChatSessionDatabaseHelper.ChatSession> allSessions = new ArrayList<>();
        private final List<ChatSessionDatabaseHelper.ChatSession> visibleSessions = new ArrayList<>();
        private String currentQuery = "";

        void setSessions(List<ChatSessionDatabaseHelper.ChatSession> sessions) {
            allSessions.clear();
            if (sessions != null) {
                allSessions.addAll(sessions);
            }
            applyFilter();
        }

        void filter(String query) {
            currentQuery = query != null ? query.trim().toLowerCase(Locale.US) : "";
            applyFilter();
        }

        private void applyFilter() {
            visibleSessions.clear();
            if (currentQuery.isEmpty()) {
                visibleSessions.addAll(allSessions);
            } else {
                for (ChatSessionDatabaseHelper.ChatSession s : allSessions) {
                    if (s.getTitle().toLowerCase(Locale.US).contains(currentQuery)) {
                        visibleSessions.add(s);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new SessionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
            final ChatSessionDatabaseHelper.ChatSession session = visibleSessions.get(position);
            holder.tvTitle.setText(session.getTitle());
            holder.tvSubtitle.setText(session.getFormattedLastPromptTime());

            boolean isActive = (session.getId() == currentSessionId);
            holder.historyItemRoot.setBackgroundResource(
                    isActive ? R.drawable.bg_session_item_active : R.drawable.bg_session_item_normal
            );

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    loadSessionIntoChat(session);
                    if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                }
            });

            holder.btnDeleteSession.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDeleteSessionConfirmDialog(session);
                }
            });
        }

        @Override
        public int getItemCount() {
            return visibleSessions.size();
        }

        class SessionViewHolder extends RecyclerView.ViewHolder {
            final LinearLayout historyItemRoot;
            final TextView tvTitle;
            final TextView tvSubtitle;
            final ImageButton btnDeleteSession;

            SessionViewHolder(@NonNull View itemView) {
                super(itemView);
                historyItemRoot = itemView.findViewById(R.id.historyItemRoot);
                tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
                tvSubtitle = itemView.findViewById(R.id.tvHistorySubtitle);
                btnDeleteSession = itemView.findViewById(R.id.btnDeleteSession);
            }
        }
    }
}
