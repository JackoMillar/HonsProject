package com.terra.FogOfEarth;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class JsonDb {

    // Database file name
    private static final String FILE_NAME = "fog_db.json";
    private static final String FILE_NAME_GZ = "fog_db.json.gz";

    private static final String TAG = "JsonDb";

    /**
     * loads json file into a JSONObject
     * @param context used to access the app’s internal storage
     * @return JSONObject
     */
    public static JSONObject load(Context context) {
        File gz = new File(context.getFilesDir(), FILE_NAME_GZ);
        File legacy = new File(context.getFilesDir(), FILE_NAME);

        // Prefer gzip; fallback to legacy json
        if (gz.exists()) {
            try (FileInputStream fis = new FileInputStream(gz);
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 InputStreamReader isr = new InputStreamReader(gis, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String raw = sb.toString().trim();
                if (raw.isEmpty()) return new JSONObject();
                return new JSONObject(raw);

            } catch (Exception e) {
                return new JSONObject();
            }
        }

        if (!legacy.exists()) return new JSONObject();

        // Parse legacy file into JSONObject
        try (FileInputStream fis = new FileInputStream(legacy);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String raw = sb.toString().trim();
            if (raw.isEmpty()) return new JSONObject();

            return new JSONObject(raw);

        } catch (Exception e) {
            // Corrupt file or parse fail
            return new JSONObject();
        }
    }

    /**
     * saves JSONObject to file
     * @param context used to access the app’s internal storage
     * @param root JSONObject to save
     */
    public static void save(Context context, JSONObject root) {
        File gz = new File(context.getFilesDir(), FILE_NAME_GZ);
        File legacy = new File(context.getFilesDir(), FILE_NAME);

        try (FileOutputStream fos = new FileOutputStream(gz);
             GZIPOutputStream gos = new GZIPOutputStream(fos)) {

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            gos.write(bytes);
            gos.finish();

        } catch (Exception ignored) {}

        // Remove legacy file once gz exists (best effort)
        if (legacy.exists()) {
            //noinspection ResultOfMethodCallIgnored
            legacy.delete();
        }
    }

    /**
     * deletes the file
     * @param context used to access the app’s internal storage
     */
    public static void clear(Context context) {
        File legacy = new File(context.getFilesDir(), FILE_NAME);
        File gz = new File(context.getFilesDir(), FILE_NAME_GZ);

        if (legacy.exists() && !legacy.delete()) {
            Log.w(TAG, "Failed to delete db file: " + legacy.getAbsolutePath());
        }
        if (gz.exists() && !gz.delete()) {
            Log.w(TAG, "Failed to delete db file: " + gz.getAbsolutePath());
        }
    }

}
