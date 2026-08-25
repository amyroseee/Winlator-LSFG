package com.winlator.widget;

import android.os.SystemClock;

/** Lightweight FPS source driven once per presented game frame. */
public class FpsCounter {
    private static final long COMPUTE_WINDOW_MS = 500;
    private static final long STALE_FPS_MS = 1500;

    private long lastTime;
    private volatile long lastFrameTime;
    private int frameCount;
    private volatile float currentFPS;

    public synchronized void tick() {
        long now = SystemClock.elapsedRealtime();
        if (lastTime == 0) lastTime = now;
        lastFrameTime = now;

        if (now >= lastTime + COMPUTE_WINDOW_MS) {
            currentFPS = (float)(frameCount * 1000L) / (now - lastTime);
            lastTime = now;
            frameCount = 0;
        }
        frameCount++;
    }

    public float getCurrentFPS() {
        long lastFrame = lastFrameTime;
        if (lastFrame == 0 || SystemClock.elapsedRealtime() - lastFrame > STALE_FPS_MS) return 0;
        return currentFPS;
    }

    public synchronized void reset() {
        lastTime = 0;
        lastFrameTime = 0;
        frameCount = 0;
        currentFPS = 0;
    }
}
