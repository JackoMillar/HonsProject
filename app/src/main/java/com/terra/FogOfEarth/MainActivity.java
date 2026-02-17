package com.terra.FogOfEarth;

import android.app.Activity;
import android.app.Application;
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

import org.json.JSONObject;
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
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_FINE_LOCATION)) ||
                        Boolean.TRUE.equals(isGranted.get(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    enableLocationTracking();
                }
            });

    // ---- Minimal Session Metrics (only what you asked for) ----
    private static int startedActivities = 0;
    private static boolean sessionActive = false;

    private static long sessionStartTs = 0L;
    private static int sessionNumber = 0;

    private static double sessionDistanceM = 0.0;

    /**
     *
     */
    private void registerAppLifecycleOnce() {
        if (lifecycleRegistered) return;
        lifecycleRegistered = true;

        Application app = getApplication();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}

            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                if (startedActivities == 1 && !sessionActive) {
                    // App entered foreground -> start session
                    sessionActive = true;
                    sessionStartTs = System.currentTimeMillis();
                    sessionNumber = StudyLogger.nextSessionNumber(activity.getApplicationContext());

                    sessionDistanceM = 0.0;
                    lastDistanceLoc = null;
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities--;
                if (startedActivities <= 0) {
                    startedActivities = 0;
                    if (sessionActive) {
                        // App entered background -> end session
                        sessionActive = false;
                        logSessionEnd(activity.getApplicationContext());
                    }
                }
            }
        });
    }
    private static Location lastDistanceLoc = null;

    private static boolean lifecycleRegistered = false;

    /**
     * Logs the end of the current session
     * @param ctx Context
     */
    private static void logSessionEnd(Context ctx) {
        try {
            long endTs = System.currentTimeMillis();
            long durationMs = (sessionStartTs > 0) ? (endTs - sessionStartTs) : 0L;

            JSONObject s = new JSONObject();
            s.put("type", "session");
            s.put("sessionId", java.util.UUID.randomUUID().toString());

            // Time spent per session
            s.put("startTs", sessionStartTs);
            s.put("endTs", endTs);
            s.put("durationMs", durationMs);

            // Number of sessions
            s.put("sessionNumber", sessionNumber);

            // Distance travelled while tracking
            s.put("sessionDistanceM", sessionDistanceM);

            // Number of times map data was shared (cumulative)
            s.put("mapShareCount", StudyLogger.getMapShareCount(ctx));

            StudyLogger.logSession(ctx, s);
        } catch (Exception ignored) {}
    }

    /**
     * <p>Called when the activity is first created.</p>
     * <p>Initialises and Creates Map & Location Overlay, User's Custom marker</p>
     * <p>Creates and sets the Settings Button and the Center Location Button</p>
     * <p>Checks Location Permission {@link #checkLocationPermission()}</p>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerAppLifecycleOnce();

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.mapView);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        mapController = map.getController();
        mapController.setZoom(12.0);

        GpsMyLocationProvider locationProvider = new GpsMyLocationProvider(ctx);
        myLocationOverlay = new MyLocationNewOverlay(locationProvider, map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);

        Drawable customMarker = ResourcesCompat.getDrawable(getResources(), R.drawable.user_marker, null);
        if (customMarker instanceof BitmapDrawable) {
            Bitmap raw = ((BitmapDrawable) customMarker).getBitmap();
            userMarkerBitmap = scaleBitmapToDp(raw);
        }

        fogOverlay = new FogOverlay(50.0f, 255, 170, 4.5);
        fogOverlay.loadAll(this);
        map.getOverlays().add(fogOverlay);

        FloatingActionButton centerLocationButton = findViewById(R.id.centerLocationButton);
        centerLocationButton.setOnClickListener(v -> centerMapOnCurrentLocation());

        FloatingActionButton settingButton = findViewById(R.id.settingButton);
        settingButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        map.getOverlays().add(new org.osmdroid.views.overlay.Overlay() {
            @Override
            public void draw(Canvas canvas, MapView mapView, boolean shadow) {
                if (shadow || userMarkerBitmap == null) return;
                GeoPoint point = myLocationOverlay.getMyLocation();
                if (point == null) return;

                Point screenPoint = new Point();
                mapView.getProjection().toPixels(point, screenPoint);

                int offsetX = userMarkerBitmap.getWidth() / 2;
                int offsetY = userMarkerBitmap.getHeight() / 2;
                canvas.drawBitmap(userMarkerBitmap, screenPoint.x - offsetX, screenPoint.y - offsetY, null);
            }
        });

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
     * <p> Starts Map and Checks Location Permission {@link #checkLocationPermission()}</p>
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

        if (fogOverlay != null) fogOverlay.loadAll(this);

        checkLocationPermission();

        if (map != null) map.invalidate();

        // Avoid double-writes while UI is visible
        sendFogServiceCommand(LocationFogService.ACTION_PAUSE);
    }

    /**
     * <p> Called when the activity is no longer visible.</p>
     * <p> Saves fog to JSON DB</p>
     * <p> Pauses Map and Stops GPS updates</p>
     * <p> {@link #sendFogServiceCommand(String action)}Resumes background tracking </p>
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (fogOverlay != null) fogOverlay.saveAll(this);

        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {}
        }

        if (map != null) {
            Configuration.getInstance().save(this, PreferenceManager.getDefaultSharedPreferences(this));
            map.onPause();
        }

        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
            myLocationOverlay.disableFollowLocation();
        }

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
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) {}
        }

        if (map != null) map.onDetach();

        sendFogServiceCommand(LocationFogService.ACTION_RESUME);
    }

    /**
     * <p> Centers the map on the user's current location.</p>
     */
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
     * <p>Enables location tracking of the user.</p>
     * <p>Starts up {@link #startFogService()} for background tracking</p>
     * <p>{@link #fogOverlay} reveals fog on startup</p>
     * <p>Starts {@link LocationListener} for GPS updates</p>
     */
    private void enableLocationTracking() {
        myLocationOverlay.disableFollowLocation();
        startFogService();

        myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint location = myLocationOverlay.getMyLocation();
            if (location != null) {
                mapController.animateTo(location);
                mapController.setZoom(16.0);
                fogOverlay.addPrimary(location);
                map.invalidate();
            }
        }));

        // prevent duplicate listeners
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored) {}
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = location -> runOnUiThread(() -> {
            if (location == null) return;

            // Distance travelled while tracking (no path stored)
            if (sessionActive) {
                if (lastDistanceLoc != null) {
                    sessionDistanceM += location.distanceTo(lastDistanceLoc);
                }
                lastDistanceLoc = location;
            }

            // Reveal fog (no tolerances)
            GeoPoint p = new GeoPoint(location.getLatitude(), location.getLongitude());
            fogOverlay.addPrimary(p);
            map.invalidate();
        });

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

    /**
     * <p> Calls {@link #sendFogServiceCommand(String action)}</p>
     */
    private void startFogService() {
        sendFogServiceCommand(null);
    }

    /**
     * Sends an optional action command to {@link LocationFogService} by starting the service.
     *
     * @param action Action string to set on the Intent, or {@code null} to start without an action.
     */
    private void sendFogServiceCommand(String action) {
        Intent i = new Intent(this, LocationFogService.class);
        if (action != null) i.setAction(action);

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (RuntimeException ignored) {}
    }

    /**
     * Converts dp to px
     *
     * @return Converted dp
     */
    private int dpToPx() {
        return Math.round((float) 48.0 * getResources().getDisplayMetrics().density);
    }

    /**
     * Scales a bitmap to a given dp size
     *
     * @param src Bitmap to scale
     * @return Scaled bitmap
     */
    private Bitmap scaleBitmapToDp(Bitmap src) {
        int px = dpToPx();
        return Bitmap.createScaledBitmap(src, px, px, true);
    }

    /**
     * If application has no location permission, request it.
     * Otherwise, call {@link #enableLocationTracking()}
     */
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