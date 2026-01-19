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

    private static final String CHANNEL_ID = "fog_location";
    private static final int NOTIF_ID = 1001;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean trackingStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        // Keep onCreate light. Foreground + tracking starts in onStartCommand.
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(NOTIF_ID, buildNotification());

        if (!trackingStarted) {
            trackingStarted = true;
            startTracking();
        }
        return START_STICKY;
    }

    private void startTracking() {
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
            // Permission not granted; service stays alive but won't receive updates.
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        trackingStarted = false;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

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
