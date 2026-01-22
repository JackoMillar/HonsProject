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

public class JsonDb {

    // Database file name
    private static final String FILE_NAME = "fog_db.json";

    private static final String TAG = "JsonDb";

    /**
     * loads json file into a JSONObject
     * @param context used to access the app’s internal storage
     * @return JSONObject
     */
    public static JSONObject load(Context context) {
        // Initialise file then check to see if it exists
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return new JSONObject();

        // Parse file into JSONObject
        try (FileInputStream fis = new FileInputStream(file);
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
        // Initialise new file
        File file = new File(context.getFilesDir(), FILE_NAME);

        // Save JSON object to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
            fos.flush();
        } catch (Exception ignored) {}
    }

    /**
     * deletes the file
     * @param context used to access the app’s internal storage
     */
    public static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return;

        boolean deleted = file.delete();
        if (!deleted) {
            Log.w(TAG, "Failed to delete db file: " + file.getAbsolutePath());
        }
    }

}
