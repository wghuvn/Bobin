package com.bobincraft.drone.client;

/** Deterministic digital video degradation. HUD telemetry is drawn after this world-only feed. */
public record DroneVideoProfile(float signal, int longestEdge, int intervalMillis, int latencyMillis, int corruptBlocks, boolean blackout) {
    public static DroneVideoProfile at(float signal, long millis) {
        float quality = Float.isFinite(signal) ? Math.max(0, Math.min(1, signal)) : 0;
        float noise = 1 - quality;
        int edge = quality >= .75 ? 1280 : quality >= .5 ? 960 : quality >= .25 ? 720 : 480;
        int interval = quality >= .75 ? 16 : quality >= .5 ? 33 : quality >= .25 ? 66 : 150;
        boolean blackout = quality <= .015f || quality < .18f && hash(millis / 180) < (int) ((.18f - quality) * 4000);
        return new DroneVideoProfile(quality, edge, interval, 35 + Math.round(noise * noise * 220),
                quality >= .8f ? 0 : Math.min(240, Math.round((.8f - quality) * 300)), blackout);
    }

    public record Size(int width, int height) { }

    /** Slight receiver pixelation is present even nearby. Keep aspect ratio and never upscale the capture. */
    public Size dimensions(int nativeWidth, int nativeHeight) {
        if (nativeWidth < 1 || nativeHeight < 1) throw new IllegalArgumentException("Empty video viewport");
        double scale = Math.min(.85, longestEdge / (double) Math.max(nativeWidth, nativeHeight));
        return new Size(Math.max(1, (int) Math.round(nativeWidth * scale)),
                Math.max(1, (int) Math.round(nativeHeight * scale)));
    }

    public boolean capture(long now, long last) {
        if (signal <= .015f) return false;
        if (last < 0) return true;
        if (now - last < intervalMillis) return false;
        // Short stalls in poor reception hold an actual old world image rather than freezing controls.
        return signal >= .35f || now - last >= 480 || hash(now / 160) >= Math.round((.35f - signal) * 1600);
    }

    public static int selectFrame(long[] times, long desired) {
        int before = -1, oldest = -1;
        for (int i = 0; i < times.length; i++) if (times[i] >= 0) {
            if (oldest < 0 || times[i] < times[oldest]) oldest = i;
            if (times[i] <= desired && (before < 0 || times[i] > times[before])) before = i;
        }
        return before >= 0 ? before : oldest;
    }

    static int hash(long value) {
        long bits = (value ^ 0x4f1bbcdcL) * 0x45d9f3bL;
        bits = (bits ^ bits >>> 16) * 0x45d9f3bL;
        return (int) Math.floorMod(bits ^ bits >>> 16, 1000);
    }
}
