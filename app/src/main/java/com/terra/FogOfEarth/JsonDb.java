package com.terra.FogOfEarth;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JsonDb {

    private static final String FILE_NAME = "fog_db.json";

    public static JSONObject load(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) return new JSONObject();

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
            // Corrupt file or parse fail -> start fresh
            return new JSONObject();
        }
    }

    public static void save(Context context, JSONObject root) {
        File file = new File(context.getFilesDir(), FILE_NAME);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
            fos.flush();
        } catch (Exception ignored) {}
    }

    public static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists()) {
            file.delete();
        }
    }

}
