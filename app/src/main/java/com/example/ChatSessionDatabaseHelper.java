package com.example;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatSessionDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dolphine_chat_history.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_SESSIONS = "chat_sessions";
    private static final String COL_SESSION_ID = "id";
    private static final String COL_SESSION_TITLE = "title";
    private static final String COL_SESSION_LAST_PROMPT_AT = "last_prompt_at";

    private static final String TABLE_MESSAGES = "chat_messages";
    private static final String COL_MSG_ID = "id";
    private static final String COL_MSG_SESSION_ID = "session_id";
    private static final String COL_MSG_IS_USER = "is_user";
    private static final String COL_MSG_CONTENT = "content";
    private static final String COL_MSG_DISPLAY_TIME = "display_time";
    private static final String COL_MSG_CREATED_AT = "created_at";

    public static class ChatSession {
        private final long id;
        private final String title;
        private final long lastPromptAt;

        public ChatSession(long id, String title, long lastPromptAt) {
            this.id = id;
            this.title = title;
            this.lastPromptAt = lastPromptAt;
        }

        public long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public long getLastPromptAt() {
            return lastPromptAt;
        }

        public String getFormattedLastPromptTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US);
            return sdf.format(new Date(lastPromptAt));
        }
    }

    public ChatSessionDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSessionsTable = "CREATE TABLE " + TABLE_SESSIONS + " ("
                + COL_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SESSION_TITLE + " TEXT NOT NULL, "
                + COL_SESSION_LAST_PROMPT_AT + " INTEGER NOT NULL"
                + ")";

        String createMessagesTable = "CREATE TABLE " + TABLE_MESSAGES + " ("
                + COL_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_MSG_SESSION_ID + " INTEGER NOT NULL, "
                + COL_MSG_IS_USER + " INTEGER NOT NULL, "
                + COL_MSG_CONTENT + " TEXT NOT NULL, "
                + COL_MSG_DISPLAY_TIME + " TEXT NOT NULL, "
                + COL_MSG_CREATED_AT + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COL_MSG_SESSION_ID + ") REFERENCES "
                + TABLE_SESSIONS + "(" + COL_SESSION_ID + ") ON DELETE CASCADE"
                + ")";

        db.execSQL(createSessionsTable);
        db.execSQL(createMessagesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    public synchronized long createSession(String firstPromptText, long timestampMillis) {
        String normalizedTitle = firstPromptText != null ? firstPromptText.trim().replaceAll("\\s+", " ") : "";
        if (normalizedTitle.isEmpty()) {
            normalizedTitle = "New Conversation";
        } else if (normalizedTitle.length() > 80) {
            normalizedTitle = normalizedTitle.substring(0, 80) + "...";
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SESSION_TITLE, normalizedTitle);
        values.put(COL_SESSION_LAST_PROMPT_AT, timestampMillis);
        return db.insert(TABLE_SESSIONS, null, values);
    }

    public synchronized void updateSessionLastPrompt(long sessionId, long timestampMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SESSION_LAST_PROMPT_AT, timestampMillis);
        db.update(
                TABLE_SESSIONS,
                values,
                COL_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)}
        );
    }

    public synchronized long insertMessage(
            long sessionId,
            boolean isUser,
            String content,
            String displayTime,
            long createdAtMillis
    ) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_MSG_SESSION_ID, sessionId);
        values.put(COL_MSG_IS_USER, isUser ? 1 : 0);
        values.put(COL_MSG_CONTENT, content != null ? content : "");
        values.put(COL_MSG_DISPLAY_TIME, displayTime != null ? displayTime : "");
        values.put(COL_MSG_CREATED_AT, createdAtMillis);
        return db.insert(TABLE_MESSAGES, null, values);
    }

    public synchronized List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_SESSIONS,
                new String[]{COL_SESSION_ID, COL_SESSION_TITLE, COL_SESSION_LAST_PROMPT_AT},
                null,
                null,
                null,
                null,
                COL_SESSION_LAST_PROMPT_AT + " DESC"
        );
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String title = cursor.getString(1);
                    long lastPromptAt = cursor.getLong(2);
                    sessions.add(new ChatSession(id, title, lastPromptAt));
                }
            } finally {
                cursor.close();
            }
        }
        return sessions;
    }

    public synchronized List<ChatAdapter.ChatMessage> getMessagesForSession(long sessionId) {
        List<ChatAdapter.ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_MESSAGES,
                new String[]{COL_MSG_IS_USER, COL_MSG_CONTENT, COL_MSG_DISPLAY_TIME},
                COL_MSG_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)},
                null,
                null,
                COL_MSG_ID + " ASC"
        );
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    boolean isUser = cursor.getInt(0) == 1;
                    String content = cursor.getString(1);
                    String displayTime = cursor.getString(2);
                    messages.add(new ChatAdapter.ChatMessage(content, isUser, displayTime));
                }
            } finally {
                cursor.close();
            }
        }
        return messages;
    }

    public synchronized void deleteSession(long sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(
                TABLE_MESSAGES,
                COL_MSG_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)}
        );
        db.delete(
                TABLE_SESSIONS,
                COL_SESSION_ID + " = ?",
                new String[]{String.valueOf(sessionId)}
        );
    }
}
