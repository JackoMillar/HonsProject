package com.terra.FogOfEarth;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class StudyLogger {

    private static final String PREFS = "study_prefs";
    private static final String KEY_PID = "participant_id";
    private static final String KEY_SESSION_NO = "session_no";
    private static final String KEY_MAP_SHARE_COUNT = "map_share_count";

    private static final String DIR = "study_logs";
    private static final String SESSIONS = "sessions.ndjson";

    private StudyLogger() {}

    public static String getParticipantId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_PID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_PID, id).apply();
        }
        return id;
    }

    public static int nextSessionNumber(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int current = sp.getInt(KEY_SESSION_NO, 0) + 1;
        sp.edit().putInt(KEY_SESSION_NO, current).apply();
        return current;
    }

    public static int incrementMapShareCount(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int current = sp.getInt(KEY_MAP_SHARE_COUNT, 0) + 1;
        sp.edit().putInt(KEY_MAP_SHARE_COUNT, current).apply();
        return current;
    }

    public static int getMapShareCount(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getInt(KEY_MAP_SHARE_COUNT, 0);
    }

    public static File getLogsDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getSessionsFile(Context ctx) {
        return new File(getLogsDir(ctx), SESSIONS);
    }

    private static void appendLine(File file, JSONObject obj) {
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            fos.write((obj.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception ignored) {}
    }

    /** Writes one session summary (NDJSON line). */
    public static void logSession(Context ctx, JSONObject sessionSummary) {
        try {
            // Always attach participant id
            sessionSummary.put("participantId", getParticipantId(ctx));
            appendLine(getSessionsFile(ctx), sessionSummary);
        } catch (Exception ignored) {}
    }
}