package com.terra.FogOfEarth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.osmdroid.util.GeoPoint;

public class LocationFogService extends Service {

    // Initialise Actions
    public static final String ACTION_PAUSE = "com.terra.FogOfEarth.action.PAUSE_BG_TRACKING";
    public static final String ACTION_RESUME = "com.terra.FogOfEarth.action.RESUME_BG_TRACKING";

    private static final String CHANNEL_ID = "fog_location";
    private static final int NOTIF_ID = 1001;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean foregroundStarted = false;
    private boolean trackingStarted = false;

    // Cached fog + filtering + save throttling
    private FogOverlay cachedFog = null;

    private long lastSaveElapsedMs = 0L;
    private static final long SAVE_THROTTLE_MS = 15_000;

    /**
     * <p>Called when the service is first created.</p>
     * <p>Calls (@link #createChannel()) to create the notification channel.</p>
     * <p>Calls (@link #startForeground()) to start the foreground service.</p>
     * <p>Calls (@link #startTracking()) to start receiving location updates.</p>
     * <p>Calls (@link #stopTracking()) if Intent == PAUSE to stop receiving location updates.</p>
     *
     * @param intent The Action intent
     * @param flags flags
     * @param startId startId
     * @return Check to see if onStartCommand worked
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create the notification channel
        createChannel();

        // Only call startForeground once.
        if (!foregroundStarted) {
            try {
                startForeground(NOTIF_ID, buildNotification());
                foregroundStarted = true;
            } catch (SecurityException se) {
                // System refused starting a LOCATION FGS in the current app state.
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        final String action = intent != null ? intent.getAction() : null;
        if (ACTION_PAUSE.equals(action)) {
            stopTracking();
            return START_STICKY;
        }

        // Default + RESUME both mean "ensure tracking is active".
        startTracking();
        return START_STICKY;
    }

    /**
     * Starts receiving GPS location updates for background fog revealing.
     */
    private void startTracking() {
        // Check if tracking has already started
        if (trackingStarted) return;
        trackingStarted = true;

        // Register for location updates
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Load fog once and keep it cached
        cachedFog = new FogOverlay(100.0f, 255, 170, 4.5);
        cachedFog.loadAll(getApplicationContext());

        locationListener = location -> {
            if (location == null || cachedFog == null) return;

            GeoPoint p = new GeoPoint(location.getLatitude(), location.getLongitude());
            cachedFog.addPrimary(p);

            long now = SystemClock.elapsedRealtime();
            if (now - lastSaveElapsedMs >= SAVE_THROTTLE_MS) {
                lastSaveElapsedMs = now;
                cachedFog.saveAll(getApplicationContext());
            }
        };

        // Requests location updates from the GPS provider and Listener
        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000,
                        2,
                        locationListener,
                        Looper.getMainLooper()
                );
            }
        } catch (SecurityException ignored) {
            // No permission; nothing to do.
        }
    }

    /**
     * Stops receiving GPS location updates for background fog revealing.
     */
    private void stopTracking() {
        // Check if tracking is already stopped
        if (!trackingStarted) return;
        trackingStarted = false;

        // Unregister for location updates
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {
            }
        }

        // Final save
        if (cachedFog != null) {
            cachedFog.saveAll(getApplicationContext());
        }
        cachedFog = null;
    }

    /**
     * When the service is destroyed, stop tracking.
     */
    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    /**
     * Creates and registers the notification channel used by the foreground service.
     */
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, // Channel ID
                    "Fog of Earth location", // Channel name
                    NotificationManager.IMPORTANCE_LOW // Channel importance
            );
            channel.setDescription("Updating explored areas in the background");

            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /**
     * This service is not designed to be bound to; it is started via startForegroundService/startService.
     * @param intent Intent to be bound
     * @return null, to show lack of binding
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Builds the notification used by the foreground service.
     * @return Notification to be displayed to the user
     */
    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fog of Earth")
                .setContentText("Updating explored areas in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }
}
