package com.terra.FogOfEarth;

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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton returnButton;

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

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingScanOptions != null) {
                    qrScanLauncher.launch(pendingScanOptions);
                } else {
                    Toast.makeText(this, "Camera permission required to scan QR codes.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        FloatingActionButton scanMapButton = findViewById(R.id.scanMapButton);
        scanMapButton.setOnClickListener(v -> launchQrScanner());

        returnButton = findViewById(R.id.returnButton);
        returnButton.setOnClickListener(v -> finish());

        // Generate QR from PRIMARY layer points
        FogOverlay tmp = new FogOverlay(100.0f, 500.0f, 255, 170, 4.5);
        tmp.loadAll(this);
        String content = tmp.exportPrimaryAsJsonArray();


        // If no data, show a tiny message JSON
        if (content == null || content.isEmpty() || content.equals("[]")) {
            content = "{\"message\":\"No progress saved yet\"}";
        }

        int qrSize = 900;
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ((ImageView) findViewById(R.id.imgQr)).setImageBitmap(bmp);

        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Shared data too large for a QR code.", Toast.LENGTH_LONG).show();
        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchQrScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan a shared map QR code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);

        pendingScanOptions = options;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            qrScanLauncher.launch(options);
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private String exportPrimaryLayerPointsAsJsonArray() {
        try {
            JSONObject root = JsonDb.load(this);
            JSONObject fog = root.optJSONObject("fog");
            if (fog == null) return "[]";

            JSONArray layers = fog.optJSONArray("layers");
            if (layers == null) return "[]";

            for (int i = 0; i < layers.length(); i++) {
                JSONObject layer = layers.optJSONObject(i);
                if (layer == null) continue;

                if ("primary".equals(layer.optString("layerId"))) {
                    JSONArray points = layer.optJSONArray("points");
                    return points != null ? points.toString() : "[]";
                }
            }
            return "[]";
        } catch (Exception e) {
            return "[]";
        }
    }

    private void handleScannedMapData(String jsonData) {
        try {
            JSONArray arr = new JSONArray(jsonData);

            // Validate basic structure (lat/lon)
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                obj.getDouble("lat");
                obj.getDouble("lon");
            }

            // Load existing db -> set shared -> save back
            FogOverlay tmp = new FogOverlay(
                    100.0f, // primary radius (must match MainActivity)
                    500.0f, // shared radius
                    255,    // solid fog
                    170,    // shared clear strength
                    4.5
            );

            tmp.loadAll(this);
            tmp.setSharedFromJsonArray(arr.toString());
            tmp.saveAll(this);

            Toast.makeText(this, "Shared map imported! Go back to the map to view.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Invalid or corrupted QR data.", Toast.LENGTH_LONG).show();
        }
    }

}
