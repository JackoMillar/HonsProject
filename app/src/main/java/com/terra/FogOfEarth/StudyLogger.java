package com.terra.FogOfEarth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class StudyLogger {

    private static final String PREFS = "study_prefs";
    private static final String KEY_PID = "participant_id";

    private static final String DIR = "study_logs";
    private static final String SESSIONS = "sessions.ndjson";
    private static final String EVENTS = "events.ndjson";

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

    public static File getLogsDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getSessionsFile(Context ctx) {
        return new File(getLogsDir(ctx), SESSIONS);
    }

    public static File getEventsFile(Context ctx) {
        return new File(getLogsDir(ctx), EVENTS);
    }

    // Append one JSON object as a single line (NDJSON)
    private static void appendLine(File file, JSONObject obj) {
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            String line = obj.toString() + "\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception ignored) {}
    }

    public static void logEvent(Context ctx, String name, JSONObject extra) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "event");
            obj.put("ts", System.currentTimeMillis());
            obj.put("participantId", getParticipantId(ctx));
            obj.put("name", name);

            obj.put("device", Build.MODEL);
            obj.put("android", Build.VERSION.SDK_INT);

            if (extra != null) obj.put("extra", extra);

            appendLine(getEventsFile(ctx), obj);
        } catch (Exception ignored) {}
    }

    public static void logSessionSummary(Context ctx, JSONObject summary) {
        try {
            JSONObject obj = new JSONObject(summary.toString()); // copy
            obj.put("type", "session");
            obj.put("participantId", getParticipantId(ctx));
            obj.put("device", Build.MODEL);
            obj.put("android", Build.VERSION.SDK_INT);

            appendLine(getSessionsFile(ctx), obj);
        } catch (Exception ignored) {}
    }
}
