package com.terra.FogOfEarth;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.List;

public class FogOverlay extends Overlay {

    // Mask so overlapping shared circles DON'T stack
    private Bitmap sharedMaskBitmap;
    private Canvas sharedMaskCanvas;

    // Paint used to draw circles into the mask (must be fully opaque)
    private final Paint sharedMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Paint used to apply the mask onto the fog layer at a fixed strength
    private final Paint sharedApplyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint fogPaint;
    private final Paint primaryEraserPaint;  // full clear

    private final List<GeoPoint> primaryPoints = new ArrayList<>();
    private final List<GeoPoint> sharedPoints = new ArrayList<>();

    private final float primaryRadiusMeters;
    private final double minDistanceMeters;

    // ----- Constructor -----

    /**
     * <p>Initialises the fog overlay.</p>
     *
     * @param RadiusMeters Radius of the primary fog circle
     * @param fogAlpha 255 = solid fog. 0 = no fog
     * @param sharedClearAlpha 255 = solid fog. 0 = no fog
     * @param minDistanceMeters Minimum distance between points / circles
     */
    public FogOverlay(
            float RadiusMeters,
            int fogAlpha,              // 255 = solid fog
            int sharedClearAlpha,      // e.g. 120–200
            double minDistanceMeters
    ) {
        super();
        this.primaryRadiusMeters = RadiusMeters;
        this.minDistanceMeters = minDistanceMeters;

        // Set up Colour and Paint of the fog
        fogPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fogPaint.setColor(0xFF4A5B6C); // fully opaque base colour
        fogPaint.setAlpha(fogAlpha);  // 255 = fully solid
        fogPaint.setStyle(Paint.Style.FILL);

        // Mask circles should be fully opaque so overlap doesn't increase intensity
        sharedMaskPaint.setColor(Color.BLACK);
        sharedMaskPaint.setStyle(Paint.Style.FILL);
        sharedMaskPaint.setAlpha(255);

        // Apply the mask ONCE to the fog layer
        sharedApplyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        sharedApplyPaint.setAlpha(sharedClearAlpha);

        // Primary full clear
        primaryEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        primaryEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        primaryEraserPaint.setAlpha(255);
    }

    /**
     * <p>Draws the fog onto the map.</p>
     * <p>Build and Apply Shared Mask</p>
     * <p>Apply Primary Mask</p>
     * @param canvas Canvas to draw onto
     * @param mapView MapView to draw onto
     * @param shadow If true, don't draw
     */
    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        int layerId;
        layerId = canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(), 255);

        // Draw solid fog onto an offscreen layer
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), fogPaint);

        // Build a SHARED mask (union of circles) then apply ONCE so overlaps don't stack
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (sharedMaskBitmap == null || sharedMaskBitmap.getWidth() != w || sharedMaskBitmap.getHeight() != h) {
            sharedMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            sharedMaskCanvas = new Canvas(sharedMaskBitmap);
        }

        // Clear mask each frame
        sharedMaskBitmap.eraseColor(Color.TRANSPARENT);

        // Draw shared circles into mask (fully opaque)
        Point sp = new Point();
        for (GeoPoint p : sharedPoints) {
            mapView.getProjection().toPixels(p, sp);
            float rPx = metersToPixels(mapView, p, primaryRadiusMeters);
            sharedMaskCanvas.drawCircle(sp.x, sp.y, rPx, sharedMaskPaint);
        }

        // Apply mask once at fixed alpha
        canvas.drawBitmap(sharedMaskBitmap, 0, 0, sharedApplyPaint);

        //  Draw PrimaryPoints onto map
        for (GeoPoint p : primaryPoints) {
            mapView.getProjection().toPixels(p, sp);
            float rPx = metersToPixels(mapView, p, primaryRadiusMeters);
            canvas.drawCircle(sp.x, sp.y, rPx, primaryEraserPaint);
        }

        canvas.restoreToCount(layerId);
    }

    /** Add point to {@link #primaryPoints}
     * @param point Point to reveal at
     */
    public void addPrimary(GeoPoint point) {
        // if point is too close to another point, do not add
        for (GeoPoint existing : primaryPoints) {
            if (existing.distanceToAsDouble(point) < minDistanceMeters) return;
        }
        primaryPoints.add(point);
    }

    /** Add points to {@link #sharedPoints}
     * @param jsonArrayString JSON Array of points to add to {@link #sharedPoints}
     */
    public void setSharedFromJsonArray(String jsonArrayString) {
        try {
            JSONArray arr = new JSONArray(jsonArrayString);
            sharedPoints.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                sharedPoints.add(new GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Load all fog layers from storage into designated {@link #primaryPoints} and {@link #sharedPoints}
     * @param context Context to load from
     */
    public void loadAll(Context context) {
        primaryPoints.clear();
        sharedPoints.clear();
        loadLayerInto(context, "primary", primaryPoints);
        loadLayerInto(context, "shared", sharedPoints);
    }

    /**
     * Save all fog layers to storage from {@link #primaryPoints} and {@link #sharedPoints}
     */
    public void saveAll(Context context) {
        saveLayerFrom(context, "primary", primaryPoints, primaryRadiusMeters);
        saveLayerFrom(context, "shared", sharedPoints, primaryRadiusMeters);
    }

    /**
     * Exports the primary points as a JSON Array
     * @return JSON Array of points as a string
     */
    public String exportPrimaryAsJsonArray() {
        try {
            JSONArray points = new JSONArray();
            for (GeoPoint p : primaryPoints) {
                JSONObject obj = new JSONObject();
                obj.put("lat", p.getLatitude());
                obj.put("lon", p.getLongitude());
                points.put(obj);
            }
            return points.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Load fog layers from storage into a list
     * @param context Context to load from
     * @param layerId Layer to load
     * @param out List to load into
     */
    private static void loadLayerInto(Context context, String layerId, List<GeoPoint> out) {
        try {
            // Load fog layers from storage
            JSONObject root = JsonDb.load(context);
            JSONObject fog = root.optJSONObject("fog");
            if (fog == null) return;

            // Load points from fog layers
            JSONArray layers = fog.optJSONArray("layers");
            if (layers == null) return;

            // Loop through all layers until found
            for (int i = 0; i < layers.length(); i++) {
                // Get layer object
                JSONObject layer = layers.optJSONObject(i);
                if (layer == null) continue;
                if (!layerId.equals(layer.optString("layerId"))) continue;

                // Get points from layer
                JSONArray points = layer.optJSONArray("points");
                if (points == null) return;

                out.clear();
                // Add points to out list
                for (int j = 0; j < points.length(); j++) {
                    JSONObject obj = points.optJSONObject(j);
                    if (obj == null) continue;
                    out.add(new GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")));
                }
                // Found layer, return
                return;
            }
        } catch (Exception ignored) {}
    }

    /**
     * Save fog layers to storage from a list
     * @param context Context to save to
     * @param layerId Layer to save
     * @param pointsList List of points to save
     * @param radiusMeters Radius of the points
     */
    private static void saveLayerFrom(Context context, String layerId, List<GeoPoint> pointsList, float radiusMeters) {
        try {
            // Load fog layers from storage
            JSONObject root = JsonDb.load(context);
            root.put("schemaVersion", 1);

            // Get fog object
            JSONObject fog = root.optJSONObject("fog");
            if (fog == null) fog = new JSONObject();

            // Get layers array
            JSONArray layers = fog.optJSONArray("layers");
            if (layers == null) layers = new JSONArray();

            // Find layer object and update
            JSONObject layerObj = null;
            int idx = -1;

            // Loop through all layers until found
            for (int i = 0; i < layers.length(); i++) {
                JSONObject lo = layers.optJSONObject(i);
                if (lo != null && layerId.equals(lo.optString("layerId"))) {
                    layerObj = lo;
                    idx = i;
                    break;
                }
            }
            if (layerObj == null) layerObj = new JSONObject();

            // Create new points array
            JSONArray arr = new JSONArray();
            for (GeoPoint p : pointsList) {
                JSONObject o = new JSONObject();
                o.put("lat", p.getLatitude());
                o.put("lon", p.getLongitude());
                arr.put(o);
            }

            // Update layer object
            layerObj.put("layerId", layerId);
            layerObj.put("revealRadiusMeters", radiusMeters);
            layerObj.put("minDistanceMeters", 4.5);
            layerObj.put("points", arr);

            // Update layers array
            if (idx >= 0) layers.put(idx, layerObj);
            else layers.put(layerObj);

            // Update fog object
            fog.put("layers", layers);
            root.put("fog", fog);

            // Save to storage
            JsonDb.save(context, root);
        } catch (Exception ignored) {}
    }

    // -- Method Helpers --

    /**
     * <p>Converts meters to pixels</p>
     *
     * @param mapView MapView to use
     * @param center Center point to use
     * @param meters Meters to convert
     * @return Converted pixels
     */
    private float metersToPixels(MapView mapView, GeoPoint center, float meters) {
        GeoPoint north = new GeoPoint(center.getLatitude() + meters / 111320f, center.getLongitude());
        Point c = new Point();
        Point n = new Point();
        mapView.getProjection().toPixels(center, c);
        mapView.getProjection().toPixels(north, n);
        return (float) Math.hypot(n.x - c.x, n.y - c.y);
    }
}
