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

import androidx.core.app.NotificationCompat;

import org.osmdroid.util.GeoPoint;

public class LocationFogService extends Service {

    public static final String ACTION_PAUSE = "com.terra.FogOfEarth.action.PAUSE_BG_TRACKING";
    public static final String ACTION_RESUME = "com.terra.FogOfEarth.action.RESUME_BG_TRACKING";

    private static final String CHANNEL_ID = "fog_location";
    private static final int NOTIF_ID = 1001;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean foregroundStarted = false;
    private boolean trackingStarted = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();

        // Only call startForeground once. Re-calling it during shutdown/background transitions
        // can throw SecurityException on newer Android versions.
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

    private void startTracking() {
        if (trackingStarted) return;
        trackingStarted = true;

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                GeoPoint p = new GeoPoint(location.getLatitude(), location.getLongitude());

                // Load -> reveal -> save
                FogOverlay tmp = new FogOverlay(100.0f, 255, 170, 4.5);
                tmp.loadAll(getApplicationContext());
                tmp.revealPrimary(p);
                tmp.saveAll(getApplicationContext());
            }
        };

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

    private void stopTracking() {
        if (!trackingStarted) return;
        trackingStarted = false;

        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fog of Earth location",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Fog of Earth")
                .setContentText("Updating explored areas in the background")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }
}