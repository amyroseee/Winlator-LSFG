package com.winlator.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.winlator.core.LSFGDiagnostic;

import java.util.Locale;

/** Compact GameHub-style FPS overlay, trimmed to the FPS metric only. */
public class PerfHudView extends View {
    private static final long REFRESH_INTERVAL_MS = 500;
    private static final int BACKGROUND_COLOR = Color.argb(218, 31, 12, 54);
    private static final int BORDER_COLOR = Color.rgb(151, 91, 255);
    private static final int LABEL_COLOR = Color.rgb(205, 177, 255);
    private static final int VALUE_COLOR = Color.WHITE;

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float padding;
    private final float radius;
    private FpsCounter fpsCounter;
    private float fps;
    private long lastRefresh;
    private long lastDiagnostic;

    public PerfHudView(Context context) {
        this(context, null);
    }

    public PerfHudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        padding = 6f * density;
        radius = 6f * density;

        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setTextSize(11f * density);
        textPaint.setLetterSpacing(0.04f);
        backgroundPaint.setColor(BACKGROUND_COLOR);
        borderPaint.setColor(BORDER_COLOR);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.4f * density);
        setWillNotDraw(false);
    }

    public void setFpsCounter(FpsCounter fpsCounter) {
        this.fpsCounter = fpsCounter;
    }

    /** Called from the present path; UI work is throttled to the Bannerlator's 500 ms cadence. */
    public void update() {
        long now = SystemClock.elapsedRealtime();
        if (lastRefresh != 0 && now < lastRefresh + REFRESH_INTERVAL_MS) return;
        lastRefresh = now;
        fps = fpsCounter != null ? fpsCounter.getCurrentFPS() : 0;
        if (lastDiagnostic == 0 || now >= lastDiagnostic + 1000) {
            lastDiagnostic = now;
            float refreshRate = getDisplay() != null ? getDisplay().getRefreshRate() : 0f;
            LSFGDiagnostic.log(String.format(Locale.ENGLISH,
                "fps-metrics gamehub_host_tick_fps=%.2f android_refresh_rate_hz=%.2f",
                fps, refreshRate));
        }
        post(() -> {
            requestLayout();
            invalidate();
        });
    }

    public void reset() {
        fps = 0;
        lastRefresh = 0;
        lastDiagnostic = 0;
        postInvalidate();
    }

    private String valueText() {
        return String.format(Locale.ENGLISH, "%.0f", fps);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText("FPS ") + textPaint.measureText(valueText());
        int width = Math.round(textWidth + padding * 2);
        int height = Math.round(metrics.descent - metrics.ascent + padding * 2);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF bounds = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);
        float halfStroke = borderPaint.getStrokeWidth() / 2f;
        bounds.inset(halfStroke, halfStroke);
        canvas.drawRoundRect(bounds, radius, radius, borderPaint);

        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = padding - metrics.ascent;
        textPaint.setColor(LABEL_COLOR);
        canvas.drawText("FPS", padding, baseline, textPaint);
        float valueX = padding + textPaint.measureText("FPS ");
        textPaint.setColor(VALUE_COLOR);
        canvas.drawText(valueText(), valueX, baseline, textPaint);
    }
}
