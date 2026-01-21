package com.terra.FogOfEarth;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MainActivity extends AppCompatActivity {

    // -- UI Variables --
    private MapView map;
    private FloatingActionButton centerLocationButton;
    private FloatingActionButton settingButton;

    // -- Map Features --
    private MyLocationNewOverlay myLocationOverlay;
    private IMapController mapController;

    // -- Fog Layer --
    private FogOverlay fogOverlay;

    private Bitmap userMarkerBitmap;

    // -- GPS updates --
    private LocationManager locationManager;
    private LocationListener locationListener;

    // -- Permissions --
    // Permission launcher
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION)) ||
                        Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    enableLocationTracking(); // Enable location tracking of user
                }
            });

    // -- Lifecycle Method --

    /**
     * <p> Called when the activity is first created.</p>
     * <p>Initialises and Creates Map & Location Overlay, User's Custom marker</p>
     * <p>Creates and sets the Settings Button and the Center Location Button</p>
     * <p>Checks Location Permission</p>
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load OSMdroid configuration
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        // Map setup
        map = findViewById(R.id.mapView);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(12.0);

        // Initialize location overlay
        GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(ctx);
        myLocationOverlay = new MyLocationNewOverlay(locationProvider, map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);

        Drawable customMarker = ResourcesCompat.getDrawable(getResources(), R.drawable.user_marker, null);
        if (customMarker instanceof BitmapDrawable) {
            Bitmap raw = ((BitmapDrawable) customMarker).getBitmap();

            // Scales User Marker
            userMarkerBitmap = scaleBitmapToDp(raw, 64f);
        }

        // --- Fog overlay ---
        fogOverlay = new FogOverlay(
                100.0f, // primary radius
                255,    // fog alpha (solid)
                170,    // shared clear alpha (tweak 120-200)
                4.5     // min distance
        );
        fogOverlay.loadAll(this);
        map.getOverlays().add(fogOverlay);

        // Center Location Button
        centerLocationButton = findViewById(R.id.centerLocationButton);
        centerLocationButton.setOnClickListener(v -> centerMapOnCurrentLocation());

        // Setting Open Button
        settingButton = findViewById(R.id.settingButton);
        settingButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // Draw the user icon centered
        map.getOverlays().add(new org.osmdroid.views.overlay.Overlay() {
            @Override
            public void draw(Canvas canvas, MapView mapView, boolean shadow) {
                if (shadow || userMarkerBitmap == null) return;
                GeoPoint point = myLocationOverlay.getMyLocation();
                if (point == null) return;

                Point screenPoint = new Point();
                mapView.getProjection().toPixels(point, screenPoint);

                // Image needs centered in the LocationOverlay
                int offsetX = userMarkerBitmap.getWidth() / 2;
                int offsetY = userMarkerBitmap.getHeight() / 2;
                canvas.drawBitmap(userMarkerBitmap, screenPoint.x - offsetX, screenPoint.y - offsetY, null);
            }
        });

        // Redraw overlay on scroll or zoom
        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                map.invalidate();
                return false;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                map.invalidate();
                return false;
            }
        });

        checkLocationPermission();
    }

    /**
     * <p> Called when the activity is about to become visible.</p>
     * <p> Starts Map and Checks Location Permission</p>
     * <p> Pauses background tracking</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onResume();
            if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        }

        // Reload fog layers (in case Settings imported shared)
        if (fogOverlay != null) fogOverlay.loadAll(this);

        // Restart GPS updates if permission granted
        checkLocationPermission();

        if (map != null) map.invalidate();

        // While the Activity is visible, pause background tracking to avoid double-writes.
        sendFogServiceCommand(LocationFogService.ACTION_PAUSE);
    }

    /**
     * <p> Called when the activity is no longer visible.</p>
     * <p> Saves fog to JSON DB</p>
     * <p> Pauses Map and Stops GPS updates</p>
     * <p> Resumes background tracking </p>
     */
    @Override
    protected void onPause() {
        super.onPause();

        // Save fog to JSON DB
        if (fogOverlay != null) fogOverlay.saveAll(this);

        // Stop GPS updates
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (map != null) {
            Configuration.getInstance().save(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onPause();
        }

        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
            myLocationOverlay.disableFollowLocation();
        }

        // App is going into the background; resume background tracking.
        sendFogServiceCommand(LocationFogService.ACTION_RESUME);
    }

    /**
     * <p> Called when the activity is about to be destroyed.</p>
     * <p> Saves fog to JSON DB</p>
     * <p> Detach Map and Stops GPS updates</p>
     * <p> Resumes background tracking </p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (fogOverlay != null) fogOverlay.saveAll(this);

        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (map != null) map.onDetach();

        sendFogServiceCommand(LocationFogService.ACTION_RESUME);
    }

    // -- Map Methods --
    private void centerMapOnCurrentLocation() {
        myLocationOverlay.disableFollowLocation();
        GeoPoint myLocation = myLocationOverlay.getMyLocation();
        if (myLocation != null) {
            mapController.animateTo(myLocation);
            mapController.setZoom(16.0);
        } else {
            Toast.makeText(this, "Location not yet found. Please wait.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Enables location tracking of the user.
     * Starts up "startFogService" for background tracking
     */
    private void enableLocationTracking() {
        myLocationOverlay.disableFollowLocation();

        // Start the location Service (tracks user location while they have the app minimised)
        startFogService();

        // Reveal fog on startup
        myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint location = myLocationOverlay.getMyLocation();
            if (location != null) {
                mapController.animateTo(location);
                mapController.setZoom(16.0);

                // Reveal PRIMARY fog
                fogOverlay.revealPrimary(location);
                map.invalidate();
            }
        }));

        // Location updates for fog
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                runOnUiThread(() -> {
                    GeoPoint newPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    fogOverlay.revealPrimary(newPoint);
                    map.invalidate();
                });
            }

        };

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000,
                    2,
                    locationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // -- Fog Services --
    private void startFogService() {
        sendFogServiceCommand(null);
    }

    private void sendFogServiceCommand(String action) {
        Intent i = new Intent(this, LocationFogService.class);
        if (action != null) i.setAction(action);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (RuntimeException ignored) {
            // Defensive: avoid crashing if the system temporarily disallows FGS starts.
        }
    }

    // -- Helper Methods --
    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private Bitmap scaleBitmapToDp(Bitmap src, float dpSize) {
        int px = dpToPx(dpSize);
        return Bitmap.createScaledBitmap(src, px, px, true);
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableLocationTracking();
        } else {
            requestPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

}