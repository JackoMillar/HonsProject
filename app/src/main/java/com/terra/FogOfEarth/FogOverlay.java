package com.terra.FogOfEarth;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.util.GeoPoint;
import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;

public class FogOverlay extends Overlay {

    private final Paint fogPaint;
    private final Paint clearPaint;
    private final List<GeoPoint> revealedPoints;
    private final float revealRadiusMeters;

    public FogOverlay(float revealRadiusMeters) {
        super();
        this.revealRadiusMeters = revealRadiusMeters;

        fogPaint = new Paint();
        fogPaint.setColor(0xffa2a3a2);
        fogPaint.setStyle(Paint.Style.FILL);

        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        revealedPoints = new ArrayList<>();
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        int saveCount = canvas.saveLayer(0, 0, canvas.getWidth(), canvas.getHeight(), null);
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), fogPaint);

        Point screenPoint = new Point();
        for (GeoPoint p : revealedPoints) {
            mapView.getProjection().toPixels(p, screenPoint);
            float radiusPixels = metersToPixels(mapView, p, revealRadiusMeters);
            canvas.drawCircle(screenPoint.x, screenPoint.y, radiusPixels, clearPaint);
        }

        canvas.restoreToCount(saveCount);
    }

    private float metersToPixels(MapView mapView, GeoPoint center, float meters) {
        // Compute a point meters north of center
        GeoPoint north = new GeoPoint(center.getLatitude() + meters / 111320f, center.getLongitude());
        Point centerPoint = new Point();
        Point northPoint = new Point();
        mapView.getProjection().toPixels(center, centerPoint);
        mapView.getProjection().toPixels(north, northPoint);
        return (float) Math.hypot(northPoint.x - centerPoint.x, northPoint.y - centerPoint.y);
    }

    public void reveal(GeoPoint point) {
        revealedPoints.add(point);
    }

    public void saveRevealedAreas(Context context) {
        JSONArray jsonArray = new JSONArray();
        for (GeoPoint point : revealedPoints) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("latitude", point.getLatitude());
                obj.put("longitude", point.getLongitude());
                jsonArray.put(obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                double latitude = obj.getDouble("latitude");
                double longitude = obj.getDouble("longitude");
                revealedPoints.add(new GeoPoint(latitude, longitude));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

