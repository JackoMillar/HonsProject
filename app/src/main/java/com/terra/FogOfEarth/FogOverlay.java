package com.terra.FogOfEarth;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

public class FogOverlay extends Overlay {

    private final Paint fogPaint;
    private final Paint clearPaint;
    private final List<GeoPoint> revealedPoints;
    private final float revealRadiusMeters;

    public FogOverlay(float revealRadiusMeters, int alpha) {
        super();
        this.revealRadiusMeters = revealRadiusMeters;

        fogPaint = new Paint();
        fogPaint.setColor(0xAA4A5B6C); // semi-transparent grey
        fogPaint.setAlpha(alpha);
        fogPaint.setStyle(Paint.Style.FILL);
        fogPaint.setAntiAlias(true);

        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        revealedPoints = new ArrayList<>();
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        // Save a new layer with alpha support
        int layerId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            layerId = canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(), 255);
        } else {
            layerId = canvas.saveLayer(0, 0, canvas.getWidth(), canvas.getHeight(), null, Canvas.ALL_SAVE_FLAG);
        }

        // Draw the fog
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), fogPaint);

        // Cut out revealed areas
        Point screenPoint = new Point();
        for (GeoPoint p : revealedPoints) {
            mapView.getProjection().toPixels(p, screenPoint);
            float radiusPixels = metersToPixels(mapView, p, revealRadiusMeters);
            canvas.drawCircle(screenPoint.x, screenPoint.y, radiusPixels, clearPaint);
        }

        // Restore canvas
        canvas.restoreToCount(layerId);
    }

    private float metersToPixels(MapView mapView, GeoPoint center, float meters) {
        GeoPoint north = new GeoPoint(center.getLatitude() + meters / 111320f, center.getLongitude());
        Point centerPoint = new Point();
        Point northPoint = new Point();
        mapView.getProjection().toPixels(center, centerPoint);
        mapView.getProjection().toPixels(north, northPoint);
        return (float) Math.hypot(northPoint.x - centerPoint.x, northPoint.y - centerPoint.y);
    }

    public void reveal(GeoPoint point) {
        final double MIN_DISTANCE_METERS = 4.5;
        for (GeoPoint existing : revealedPoints) {
            if (existing.distanceToAsDouble(point) < MIN_DISTANCE_METERS) return;
        }
        revealedPoints.add(point);
    }

    public void saveRevealedAreas(Context context) {
        JSONArray jsonArray = new JSONArray();
        for (GeoPoint point : revealedPoints) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("latitude", point.getLatitude());
                obj.put("longitude", point.getLongitude());
                jsonArray.put(obj);
            } catch (Exception ignored) {}
        }
        SharedPreferences prefs = context.getSharedPreferences("fog_data", Context.MODE_PRIVATE);
        prefs.edit().putString("revealed_points", jsonArray.toString()).apply();
    }

    public void loadRevealedAreas(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("fog_data", Context.MODE_PRIVATE);
        String json = prefs.getString("revealed_points", null);
        if (json == null) return;
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                revealedPoints.add(new GeoPoint(obj.getDouble("latitude"), obj.getDouble("longitude")));
            }
        } catch (Exception ignored) {}
    }

    public void loadFromJson(String json) {
        if (json == null) return;
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                revealedPoints.add(new GeoPoint(obj.getDouble("latitude"), obj.getDouble("longitude")));
            }
        } catch (Exception ignored) {}
    }
}
