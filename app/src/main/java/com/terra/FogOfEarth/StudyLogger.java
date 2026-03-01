package com.terra.FogOfEarth;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

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

    // Current session state (persisted so service + activity can update it)
    private static final String KEY_CUR_SESSION_ID = "cur_session_id";
    private static final String KEY_CUR_SESSION_START = "cur_session_start";
    private static final String KEY_CUR_SESSION_NO = "cur_session_no";
    private static final String KEY_CUR_DISTANCE_MM = "cur_distance_mm"; // long, millimetres

    private static final String KEY_LAST_LOC_HAS = "last_loc_has";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";

    // Used to decide whether a background period should split sessions
    private static final String KEY_LAST_BG_TS = "last_bg_ts";
    // If the app is backgrounded longer than this, next foreground starts a new session.
    private static final long SESSION_BREAK_MS = 5 * 60 * 1000; // 5 minutes

    private static final String DIR = "study_logs";
    private static final String SESSIONS = "sessions.ndjson";

    private StudyLogger() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getParticipantId(Context ctx) {
        SharedPreferences sp = sp(ctx);
        String id = sp.getString(KEY_PID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            sp.edit().putString(KEY_PID, id).apply();
        }
        return id;
    }

    public static int nextSessionNumber(Context ctx) {
        SharedPreferences sp = sp(ctx);
        int current = sp.getInt(KEY_SESSION_NO, 0) + 1;
        sp.edit().putInt(KEY_SESSION_NO, current).apply();
        return current;
    }

    public static int incrementMapShareCount(Context ctx) {
        SharedPreferences sp = sp(ctx);
        int current = sp.getInt(KEY_MAP_SHARE_COUNT, 0) + 1;
        sp.edit().putInt(KEY_MAP_SHARE_COUNT, current).apply();
        return current;
    }

    public static int getMapShareCount(Context ctx) {
        return sp(ctx).getInt(KEY_MAP_SHARE_COUNT, 0);
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

    // ------------------------
    // Session + distance logic
    // ------------------------

    /** Call when the app comes to foreground (first Activity started). */
    public static void onAppForeground(Context ctx) {
        SharedPreferences sp = sp(ctx);
        long now = System.currentTimeMillis();
        long lastBg = sp.getLong(KEY_LAST_BG_TS, 0L);

        // If we had a session and were away for long enough, end it and start a new one.
        if (sp.getString(KEY_CUR_SESSION_ID, null) != null && lastBg > 0L && (now - lastBg) > SESSION_BREAK_MS) {
            endCurrentSession(ctx, now);
        }

        // Clear background marker
        sp.edit().putLong(KEY_LAST_BG_TS, 0L).apply();

        // Ensure we have an active session
        ensureSessionStarted(ctx, now);
    }

    /** Call when the app goes to background (last Activity stopped). */
    public static void onAppBackground(Context ctx) {
        sp(ctx).edit().putLong(KEY_LAST_BG_TS, System.currentTimeMillis()).apply();
        // Do NOT end the session here; we keep counting distance while backgrounded.
    }

    /** Ensures a current session exists. */
    public static void ensureSessionStarted(Context ctx, long now) {
        SharedPreferences sp = sp(ctx);
        if (sp.getString(KEY_CUR_SESSION_ID, null) != null) return;

        String id = UUID.randomUUID().toString();
        int sessionNo = nextSessionNumber(ctx);

        sp.edit()
                .putString(KEY_CUR_SESSION_ID, id)
                .putLong(KEY_CUR_SESSION_START, now)
                .putInt(KEY_CUR_SESSION_NO, sessionNo)
                .putLong(KEY_CUR_DISTANCE_MM, 0L)
                .putBoolean(KEY_LAST_LOC_HAS, false)
                .apply();
    }

    /** Adds distance from the last stored point to this location (if a session is active). */
    public static void addDistanceSample(Context ctx, Location loc) {
        if (loc == null) return;

        SharedPreferences sp = sp(ctx);
        long now = System.currentTimeMillis();
        ensureSessionStarted(ctx, now);

        boolean hasLast = sp.getBoolean(KEY_LAST_LOC_HAS, false);
        double lastLat = Double.longBitsToDouble(sp.getLong(KEY_LAST_LAT, Double.doubleToLongBits(0.0)));
        double lastLon = Double.longBitsToDouble(sp.getLong(KEY_LAST_LON, Double.doubleToLongBits(0.0)));

        long distMm = sp.getLong(KEY_CUR_DISTANCE_MM, 0L);

        if (hasLast) {
            float[] out = new float[1];
            Location.distanceBetween(lastLat, lastLon, loc.getLatitude(), loc.getLongitude(), out);
            float dM = out[0];
            // ignore jitter; ignore absurd jumps
            if (dM >= 0.5f && dM < 2000f) {
                distMm += (long) Math.round(dM * 1000.0);
            }
        }

        sp.edit()
                .putLong(KEY_CUR_DISTANCE_MM, distMm)
                .putBoolean(KEY_LAST_LOC_HAS, true)
                .putLong(KEY_LAST_LAT, Double.doubleToLongBits(loc.getLatitude()))
                .putLong(KEY_LAST_LON, Double.doubleToLongBits(loc.getLongitude()))
                .apply();
    }

    public static double getCurrentDistanceM(Context ctx) {
        return sp(ctx).getLong(KEY_CUR_DISTANCE_MM, 0L) / 1000.0;
    }

    /** Ends the current session (writes one NDJSON line) and clears current-session state. */
    public static void endCurrentSession(Context ctx, long endTs) {
        SharedPreferences sp = sp(ctx);
        String sessionId = sp.getString(KEY_CUR_SESSION_ID, null);
        if (sessionId == null) return;

        long startTs = sp.getLong(KEY_CUR_SESSION_START, 0L);
        int sessionNo = sp.getInt(KEY_CUR_SESSION_NO, 0);
        long distMm = sp.getLong(KEY_CUR_DISTANCE_MM, 0L);

        long durationMs = (startTs > 0L && endTs >= startTs) ? (endTs - startTs) : 0L;

        try {
            JSONObject s = new JSONObject();
            s.put("type", "session");
            s.put("sessionId", sessionId);

            s.put("startTs", startTs);
            s.put("endTs", endTs);
            s.put("durationMs", durationMs);

            s.put("sessionNumber", sessionNo);
            s.put("sessionDistanceM", distMm / 1000.0);

            s.put("mapShareCount", getMapShareCount(ctx));

            logSession(ctx, s);
        } catch (Exception ignored) {}

        sp.edit()
                .remove(KEY_CUR_SESSION_ID)
                .remove(KEY_CUR_SESSION_START)
                .remove(KEY_CUR_SESSION_NO)
                .remove(KEY_CUR_DISTANCE_MM)
                .putBoolean(KEY_LAST_LOC_HAS, false)
                .apply();
    }
}