package com.terra.FogOfEarth;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.util.GeoPoint;

public class SettingsActivity extends AppCompatActivity {

    // -- QR Scanner --
    // Runtime camera permission
    private ScanOptions pendingScanOptions;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScannedMapData(result.getContents());
                } else {
                    Toast.makeText(this, "No QR code detected.", Toast.LENGTH_SHORT).show();
                }
            });

    // Camera permission launcher
    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingScanOptions != null) {
                    qrScanLauncher.launch(pendingScanOptions);
                } else {
                    Toast.makeText(this, "Camera permission required to scan QR codes.", Toast.LENGTH_LONG).show();
                }
            });

    // Multipart QR state (FOG2)
    private String[] qrParts = null;
    private int qrPartIndex = 0;

    // Import multipart collector (in-memory for this session)
    private String importTransferId = null;
    private String[] importParts = null;
    private int importTotal = 0;

    /**
     * <p>Called when the activity is first created.</p>
     * <p>Sets up UI buttons:</p>
     * <ul>
     *      <li>{@code  #scanQrButton} launches {@link #launchQrScanner()}</li>
     *      <li>{@code  #returnButton} returns to the previous activity</li>
     *      <li>{@code  #clearCacheButton} clears the cached data</li>
     * </ul>
     * @param savedInstanceState previously saved state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        // Apply padding so UI content is not hidden
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Button: launch QR scanner to import map progress from another device
        MaterialButton scanQrButton = findViewById(R.id.scanMapButton);
        scanQrButton.setOnClickListener(v -> launchQrScanner());

        // Button: return to main activity
        ImageButton returnButton = findViewById(R.id.returnButton);
        returnButton.setOnClickListener(v -> finish());

        // Generate QR from saved PRIMARY layer points
        FogOverlay tmp = new FogOverlay(100.0f, 255, 170, 4.5);
        tmp.loadAll(this);

        // QR image size in pixels
        int qrSize = 900;

        // Build QR payload(s) - NEW: self-contained segments (FOG3)
        final int MAX_SEG_LEN = 800; // keep it smaller = scans easier
        String transferId = String.valueOf(System.currentTimeMillis());

        List<GeoPoint> primaryPts = tmp.getPrimaryPointsCopy();

        if (primaryPts.isEmpty()) {
            qrParts = new String[] { "FOG3|EMPTY|1/1|" };
        } else {
            ArrayList<String> chunks = new ArrayList<>();
            ArrayList<GeoPoint> seg = new ArrayList<>();

            for (GeoPoint p : primaryPts) {
                seg.add(p);
                String enc = FogOverlay.encodePolylinePublic(seg);

                // If segment got too big, finalise previous segment and start a new one
                if (enc.length() > MAX_SEG_LEN && seg.size() > 1) {
                    GeoPoint last = seg.remove(seg.size() - 1);
                    chunks.add(FogOverlay.encodePolylinePublic(seg));
                    seg.clear();
                    seg.add(last);
                }
            }

            if (!seg.isEmpty()) chunks.add(FogOverlay.encodePolylinePublic(seg));

            int total = chunks.size();
            qrParts = new String[total];
            for (int i = 0; i < total; i++) {
                qrParts[i] = "FOG3|" + transferId + "|" + (i + 1) + "/" + total + "|" + chunks.get(i);
            }
        }

        ImageView qrView = findViewById(R.id.imgQr);
        renderQrPart(qrView, qrParts[0], qrSize);

        // Tap QR to cycle parts if multiple
        qrView.setOnClickListener(v -> {
            if (qrParts == null || qrParts.length <= 1) return;
            qrPartIndex = (qrPartIndex + 1) % qrParts.length;
            renderQrPart(qrView, qrParts[qrPartIndex], qrSize);
            Toast.makeText(this,
                    "QR part " + (qrPartIndex + 1) + " / " + qrParts.length,
                    Toast.LENGTH_SHORT).show();
        });

        MaterialButton exportButton = findViewById(R.id.exportLogsButton);
        exportButton.setOnClickListener(v -> {
            try {
                java.io.File zip = StudyExport.exportZip(this);
                if (zip == null || !zip.exists()) {
                    Toast.makeText(this, "Export failed: zip not created.", Toast.LENGTH_LONG).show();
                    return;
                }
                StudyExport.shareZip(this, zip);
            } catch (Exception e) {
                Toast.makeText(this, "Export failed: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });



        // Button: clear cached / saved fog data
        MaterialButton clearCacheButton = findViewById(R.id.clearCacheButton);
        clearCacheButton.setOnClickListener(v -> {
            JsonDb.clear(this);
            ((ImageView) findViewById(R.id.imgQr)).setImageResource(R.drawable.placeholder_qr);
            Toast.makeText(this, "Cache cleared.", Toast.LENGTH_SHORT).show();
        });


    }

    /**
     * <p>Launches the QR Scanner Activity</p>
     * <p>If camera permission is not granted, requests it</p>
     */
    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a shared map QR code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);

        pendingScanOptions = options;

        // Checks for permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            qrScanLauncher.launch(options);
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * <p>Handles the scanned QR code, saving it to the shared layer</p>
     * @param jsonData scanned data
     */
    private void handleScannedMapData(String jsonData) {
        // NEW format: FOG3|transferId|part/total|encodedSegment
        if (jsonData != null && jsonData.startsWith("FOG3|")) {
            if (jsonData.equals("FOG3|EMPTY|1/1|")) {
                Toast.makeText(this, "That QR contains no progress.", Toast.LENGTH_LONG).show();
                return;
            }

            String[] parts = jsonData.split("\\|", 4);
            if (parts.length < 4) throw new IllegalArgumentException("Bad FOG3 payload");

            String tId = parts[1];
            String frac = parts[2];
            String chunk = parts[3];

            String[] ft = frac.split("/");
            int partNum = Integer.parseInt(ft[0]);
            int total = Integer.parseInt(ft[1]);

            // (Re)initialise collector if new transfer
            if (importTransferId == null || !importTransferId.equals(tId)) {
                importTransferId = tId;
                importTotal = total;
                importParts = new String[total];
            }

            if (partNum < 1 || partNum > total) {
                Toast.makeText(this, "Bad QR part index.", Toast.LENGTH_LONG).show();
                return;
            }

            // ignore duplicates
            if (importParts[partNum - 1] != null) {
                Toast.makeText(this, "Already scanned part " + partNum + " / " + total, Toast.LENGTH_SHORT).show();
                return;
            }

            importParts[partNum - 1] = chunk;

            // ✅ Append this scanned QR immediately
            FogOverlay tmp = new FogOverlay(100.0f, 255, 170, 4.5);
            tmp.loadAll(this);
            int added = tmp.appendSharedFromEncodedPolyline(chunk);
            tmp.saveAll(this);

            int have = 0;
            for (String s : importParts) if (s != null) have++;

            Toast.makeText(this,
                    "Added " + added + " points from part " + partNum + " / " + total +
                            " (" + have + " scanned)",
                    Toast.LENGTH_LONG).show();

            // Optional: only count a “full share” when all parts scanned
            if (have == total) {
                StudyLogger.incrementMapShareCount(this);
                importTransferId = null;
                importParts = null;
                importTotal = 0;
            }
            return;
        }
        Toast.makeText(this, "Unrecognised QR format.", Toast.LENGTH_LONG).show();
    }

    private void renderQrPart(ImageView view, String content, int sizePx) {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L); // max capacity
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            // Optional: reduce quiet zone a bit (default is 4). Lower = more dense, sometimes harder to scan.
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            view.setImageBitmap(bmp);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Shared data too large for a single QR.", Toast.LENGTH_LONG).show();
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR.", Toast.LENGTH_LONG).show();
        }
    }

}
