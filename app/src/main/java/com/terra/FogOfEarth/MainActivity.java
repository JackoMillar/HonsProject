package com.terra.FogOfEarth;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

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

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * The main activity for the application, handling map initialization,
 * location permission requests, and user location tracking using OSMdroid.
 */
public class MainActivity extends AppCompatActivity {

    // --- Member Variables ---
    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private IMapController mapController;
    private FloatingActionButton centerLocationButton;


    // --- Permission Handling ---

    /**
     * Registers a launcher for requesting multiple runtime permissions. (e.g. location tracking)
     * This handles the result of the permission request process.
     */
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                // Check if either FINE or COARSE location permission was granted
                if (Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION)) ||
                        Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {

                    // Permissions granted, proceed to enable location services
                    enableLocationTracking();
                }
                // else: Permissions were denied. A full application would typically
                // inform the user or degrade gracefully (e.g., show a default, non-centered map).
            });

    // --- Activity Lifecycle ---

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMdroid Configuration: This must be done before setContentView()
        Context ctx = getApplicationContext();
        // Load the OSMdroid configuration (user agent, cache paths, etc.)
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        // MapView Setup
        map = findViewById(R.id.mapView);
        map.setTileSource(TileSourceFactory.MAPNIK); // Use the standard MAPNIK tiles
        map.setMultiTouchControls(true);             // Enable pinch-to-zoom
        mapController = map.getController();
        mapController.setZoom(12.0);                 // Set initial default zoom level

        // Location Overlay Initialization
        GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(ctx);
        // Create the overlay that draws the user's location on the map
        myLocationOverlay = new MyLocationNewOverlay(locationProvider, map);

        // Disable compass/directional arrow

        try{
            Drawable customMarker = ResourcesCompat.getDrawable(getResources(), R.drawable.user_marker, null);

            if (customMarker != null) {
                Bitmap markerBitmap = ((android.graphics.drawable.BitmapDrawable) customMarker).getBitmap();

                myLocationOverlay.setDirectionIcon(markerBitmap);            }
        } catch (Exception e) {
            // Handle error
            e.printStackTrace();
            Toast.makeText(this, "Error loading custom marker.", Toast.LENGTH_LONG).show();
        }

        // Add the location overlay to the map's list of overlays
        map.getOverlays().add(myLocationOverlay);

        // Floating Action Button Setup
        centerLocationButton = findViewById(R.id.centerLocationButton);

        // Set the click listener to center the map on the current location
        centerLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                centerMapOnCurrentLocation();
            }
        });

        // Start the permission check and location tracking process
        checkLocationPermission();
    }

    /**
     * Centers the map view on the last known current location of the user.
     */
    private void centerMapOnCurrentLocation() {
        // Disable automatic following to allow the user to pan manually after a click
        myLocationOverlay.disableFollowLocation();

        // Get the last known location from the overlay
        GeoPoint myLocation = myLocationOverlay.getMyLocation();

        if (myLocation != null) {
            // Animate the map to the location for a smooth transition
            mapController.animateTo(myLocation);
            mapController.setZoom(16.0); // Set a good, close zoom level
        } else {
            // Incase location is null, send error to user
            Toast.makeText(this, "Location not yet found. Please wait.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Checks if the required location permissions are granted.
     * Requests permissions if they are not already granted.
     */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Permissions already granted: proceed
            enableLocationTracking();
        } else {
            // Permissions not granted: request them
            requestPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    /**
     * Enables the location-sensing hardware and tracking on the map overlay.
     * This is called after permissions have been successfully granted.
     */
    private void enableLocationTracking() {
        // Enable the location provider and start drawing the user's position
        myLocationOverlay.enableMyLocation();

        // Set the overlay to automatically follow the user's location updates
        myLocationOverlay.enableFollowLocation();

        // Use runOnFirstFix to center the map immediately when the *first* location
        // fix is received, ensuring the user starts at their current spot.
        myLocationOverlay.runOnFirstFix(() -> {
            // Ensure UI updates (like map movement) happen on the main thread
            runOnUiThread(() -> {
                GeoPoint location = myLocationOverlay.getMyLocation();
                if (location != null) {
                    mapController.animateTo(location);
                    mapController.setZoom(16.0); // Zoom in closer on user's spot
                }
            });
        });
    }

    // --- Map Lifecycle Management ---
    // --- Paused, Resumed, or Destroyed (closed) ---

    /**
     * Called when the activity is foregrounded.
     * Reloads map configuration and re-enables location tracking.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (map != null) {
            // Reload configuration to handle potential changes
            Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onResume(); // Call map's onResume
            // Re-enable location updates when the app is foregrounded
            if (myLocationOverlay != null) {
                myLocationOverlay.enableMyLocation();
            }
        }
    }

    /**
     * Called when the activity is backgrounded or partially obscured.
     * Saves map configuration and stops location tracking to save battery.
     * This needs changed at a later date once map layer revealing has been added
     */
    @Override
    public void onPause() {
        super.onPause();
        if (map != null) {
            // Save configuration state
            Configuration.getInstance().save(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onPause(); // Call map's onPause
            // Stop location updates when the app is backgrounded
            if (myLocationOverlay != null) {
                myLocationOverlay.disableMyLocation();
            }
        }
    }

    /**
     * Called when the activity is finally destroyed.
     * Cleans up map resources.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (map != null) {
            // Release map resources to prevent memory leaks
            map.onDetach();
        }
    }
}