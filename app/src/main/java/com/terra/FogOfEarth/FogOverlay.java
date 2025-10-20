package com.terra.FogOfEarth;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

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
}

