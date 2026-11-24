package app.yike.focus;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Chronometer;
import org.json.JSONObject;

final class FloatingTimer {
    private static Chronometer clock;
    private static WindowManager.LayoutParams position;

    private FloatingTimer() {}

    static void update(Context context, JSONObject state, PendingIntent openApp) {
        boolean running = "running".equals(state.optString("status"));
        boolean paused = "paused".equals(state.optString("status"));
        boolean countdown = state.optBoolean("countdown");
        long when = state.optLong("when");
        long remaining = when - System.currentTimeMillis();
        if ((!running && !paused) || (running && countdown && remaining <= 0)
            || !TimerAlerts.preferences(context).getBoolean("floating", false) || !Settings.canDrawOverlays(context)) {
            hide(context);
            return;
        }
        Context app = context.getApplicationContext();
        if (clock == null && !show(app, openApp)) return;
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(state.optBoolean("rest") ? 0xff255c85 : 0xff922f45);
        pill.setCornerRadius(px(app, 24));
        pill.setStroke(px(app, 1), 0x57ffffff);
        clock.setBackground(pill);
        clock.setContentDescription(state.optString("title"));
        clock.setCountDown(countdown);
        if (running) {
            clock.setBase(SystemClock.elapsedRealtime() + remaining);
            clock.start();
        } else {
            clock.stop();
            clock.setText(state.optString("detail"));
        }
    }

    private static boolean show(Context context, PendingIntent openApp) {
        WindowManager windows = context.getSystemService(WindowManager.class);
        WindowManager.LayoutParams previous = position;
        position = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        position.gravity = Gravity.TOP | Gravity.LEFT;
        position.setTitle("Yike floating timer");
        int maxX = Math.max(0, context.getResources().getDisplayMetrics().widthPixels - px(context, 104));
        position.x = previous == null ? Math.max(0, maxX - px(context, 28)) : Math.min(maxX, previous.x);
        position.y = previous == null ? px(context, 72) : previous.y;
        clock = new Chronometer(context);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(px(context, 13), 0, px(context, 13), 0);
        clock.setMinHeight(px(context, 44));
        clock.setMinWidth(px(context, 100));
        clock.setTextSize(17);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setOnClickListener(view -> {
            try { openApp.send(); } catch (PendingIntent.CanceledException ignored) {}
        });
        clock.setOnTouchListener(new DragClock(windows, context));
        try {
            windows.addView(clock, position);
            return true;
        } catch (WindowManager.BadTokenException | SecurityException unavailable) {
            clock = null;
            return false;
        }
    }

    static void hide(Context context) {
        if (clock == null) return;
        clock.stop();
        try {
            context.getSystemService(WindowManager.class).removeView(clock);
        } catch (IllegalArgumentException alreadyRemoved) {
            // Android can remove an overlay when its permission changes.
        }
        clock = null;
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static final class DragClock implements android.view.View.OnTouchListener {
        private final WindowManager windows;
        private final Context context;
        private float startX;
        private float startY;
        private int originalX;
        private int originalY;
        private boolean dragging;

        DragClock(WindowManager windows, Context context) {
            this.windows = windows;
            this.context = context;
        }

        @Override
        public boolean onTouch(android.view.View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    originalX = position.x;
                    originalY = position.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;
                    dragging |= Math.hypot(dx, dy) > ViewConfiguration.get(context).getScaledTouchSlop();
                    if (dragging) {
                        int width = context.getResources().getDisplayMetrics().widthPixels - view.getWidth();
                        int height = context.getResources().getDisplayMetrics().heightPixels - view.getHeight() - px(context, 40);
                        position.x = Math.max(0, Math.min(width, originalX + Math.round(dx)));
                        position.y = Math.max(0, Math.min(height, originalY + Math.round(dy)));
                        windows.updateViewLayout(view, position);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) view.performClick();
                    return true;
                default:
                    return false;
            }
        }
    }
}
