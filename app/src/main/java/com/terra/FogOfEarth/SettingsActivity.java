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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;



import com.google.android.material.button.MaterialButton;


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
        // Load existing db -> set shared -> save back
        FogOverlay tmp = new FogOverlay(100.0f, 255, 170, 4.5);
        tmp.loadAll(this);
        String content = tmp.exportPrimaryAsJsonArray();

        // If no data, show a tiny message JSON
        if (content == null || content.isEmpty() || content.equals("[]")) {
            content = "{\"message\":\"No progress saved yet\"}";
        }

        // QR image size in pixels
        int qrSize = 900;

        // writer to convert JSON into a QR code
        QRCodeWriter writer = new QRCodeWriter();
        try {
            // Encode content as a QR code matrix
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize);

            // Create a bitmap to draw the QR code into
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // Convert the BitMatrix into black and white pixels in the bitmap
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            // Display the generated QR bitmap in the ImageView
            ((ImageView) findViewById(R.id.imgQr)).setImageBitmap(bmp);

        } catch (IllegalArgumentException e) {
            // Throw if content cannot be encoded
            Toast.makeText(this, "Shared data too large for a QR code.", Toast.LENGTH_LONG).show();
        } catch (WriterException e) {
            // Throw if writer fails to generate QR code
            Toast.makeText(this, "Failed to generate QR.", Toast.LENGTH_LONG).show();
        }

        // Button: clear cached / saved fog data
        MaterialButton clearCacheButton = findViewById(R.id.clearCacheButton);
        clearCacheButton.setOnClickListener(v -> {
            // Clear stored JSON DB/cache data
            JsonDb.clear(this);

            // Reset the QR Image to a placeholder since progress is now wiped
            ((ImageView) findViewById(R.id.imgQr)).setImageResource(R.drawable.placeholder_qr);

            // Inform user that cache is cleared
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
                    100.0f, // primary radius
                    255,    // solid fog
                    170,    // shared clear strength
                    4.5
            );

            // Load array into fog overlay and save
            tmp.loadAll(this);
            tmp.setSharedFromJsonArray(arr.toString());
            tmp.saveAll(this);

            Toast.makeText(this, "Shared map imported! Go back to the map to view.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // throw if data is invalid or corrupted
            Toast.makeText(this, "Invalid or corrupted QR data.", Toast.LENGTH_LONG).show();
        }
    }

}
