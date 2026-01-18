package com.terra.FogOfEarth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton returnButton;

    private final ActivityResultLauncher<ScanOptions> qrScanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScannedMapData(result.getContents());
                } else {
                    Toast.makeText(this, "No QR code detected.", Toast.LENGTH_SHORT).show();
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
        scanMapButton.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan a shared map QR code");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            qrScanLauncher.launch(options);
        });

        returnButton = findViewById(R.id.returnButton);
        returnButton.setOnClickListener(v -> finish());


        SharedPreferences prefs = getSharedPreferences("fog_data", MODE_PRIVATE);
        String content = prefs.getString("revealed_points", null);

        if (content == null || content.isEmpty()) {
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

    private void handleScannedMapData(String jsonData) {
        try {
            // Save the imported fog JSON so MainActivity can use it
            getSharedPreferences("fog_data", MODE_PRIVATE)
                    .edit()
                    .putString("shared_fog_overlay", jsonData)
                    .apply();

            Toast.makeText(this, "Shared map imported! Restart main map to view.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Invalid or corrupted QR data.", Toast.LENGTH_LONG).show();
        }
    }
}