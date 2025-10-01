package com.terra.FogOfEarth;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import androidx.preference.PreferenceManager; // Ensure this is imported

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private IMapController mapController;
    private FloatingActionButton centerLocationButton;


    // Register a launcher for requesting permissions
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION)) ||
                        Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {

                    // Permissions granted, enable and center the map
                    enableLocationTracking();
                }
                // else: Handle case where permissions are denied (e.g., show a default location)
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMdroid Configuration
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.mapView);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(12.0); // Default zoom level

        // Initialize Location Overlay
        GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(ctx);
        myLocationOverlay = new MyLocationNewOverlay(locationProvider, map);

        // Add the location overlay to the map
        map.getOverlays().add(myLocationOverlay);

        // 1. Initialize the button
        centerLocationButton = findViewById(R.id.centerLocationButton);

        // 2. Set the click listener
        centerLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                centerMapOnCurrentLocation();
            }
        });

        checkLocationPermission();
    }

    private void centerMapOnCurrentLocation() {
        // Stop following the user if it was enabled, to allow manual centering
        myLocationOverlay.disableFollowLocation();

        // Get the current location
        GeoPoint myLocation = myLocationOverlay.getMyLocation();

        if (myLocation != null) {
            // Animate the map to the location
            mapController.animateTo(myLocation);
            mapController.setZoom(16.0); // Set a good zoom level for the user's location
        } else {
            // If location is not yet known (e.g., first few seconds after app start)
            // You might want to show a Toast message here.
            // Example: Toast.makeText(this, "Location not yet found. Please wait.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Permissions already granted
            enableLocationTracking();
        } else {
            // Request permissions at runtime
            requestPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void enableLocationTracking() {
        // 1. Enable tracking
        myLocationOverlay.enableMyLocation();

        // 2. Set to automatically center the map on the user's location
        // Note: This only happens the *first* time location is found or when the user taps the button (if you add one).
        myLocationOverlay.enableFollowLocation();
        // Optional: Set a listener to center the map immediately when the first location is available
        myLocationOverlay.runOnFirstFix(() -> {
            runOnUiThread(() -> {
                // GeoPoint is null if no location is immediately available
                GeoPoint location = myLocationOverlay.getMyLocation();
                if (location != null) {
                    mapController.animateTo(location);
                    mapController.setZoom(16.0); // Zoom in closer on user's spot
                }
            });
        });
    }

    // Map Lifecycle Management
    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onResume();
            // Start location updates when the app is foregrounded
            if (myLocationOverlay != null) {
                myLocationOverlay.enableMyLocation();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            Configuration.getInstance().save(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onPause();
            // Stop location updates to save battery when the app is backgrounded
            if (myLocationOverlay != null) {
                myLocationOverlay.disableMyLocation();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (map != null) {
            map.onDetach();
        }
    }
}