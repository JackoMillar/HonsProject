package com.terra.FogOfEarth;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class StudyExport {

    private StudyExport() {}

    public static File exportZip(Context ctx) {
        File sessions = StudyLogger.getSessionsFile(ctx);
        File events = StudyLogger.getEventsFile(ctx);

        File out = new File(ctx.getFilesDir(), "study_export_" + System.currentTimeMillis() + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            addFileToZip(zos, sessions, "sessions.ndjson");
            addFileToZip(zos, events, "events.ndjson");
        } catch (Exception e) {
            return null;
        }

        return out;
    }


    private static void addFileToZip(ZipOutputStream zos, File file, String entryName) throws Exception {
        if (file == null || !file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);

            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                zos.write(buf, 0, read);
            }
            zos.closeEntry();
        }
    }

    public static void shareZip(Context ctx, File zip) {
        if (zip == null || !zip.exists()) return;

        Uri uri = FileProvider.getUriForFile(
                ctx,
                ctx.getPackageName() + ".fileprovider",
                zip
        );

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/zip");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(android.content.ClipData.newRawUri("study_logs", uri));

        ctx.startActivity(Intent.createChooser(share, "Export study logs"));
    }
}
