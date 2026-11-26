package app.yike.focus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TimerAlerts {
    static final int ALARM_NOTIFICATION = 102;
    static final String TIMER_DUE = "app.yike.focus.TIMER_DUE";
    static final String DISMISS = "app.yike.focus.DISMISS_BREAK";
    static final String CONTROL = "app.yike.focus.CONTROL_TIMER";
    private static final int TIMER_NOTIFICATION = 101;
    private static final String TIMER_CHANNEL = "timer";
    private static final String ALARM_CHANNEL = "break-alarm";
    private static final String FALLBACK_ALARM_CHANNEL = "timer-alarm-fallback";
    private static final long CHIME_GRACE = 60_000L;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
    private static JSONObject latestTimer;
    private static volatile boolean appVisible;

    private TimerAlerts() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("timer-alerts", Context.MODE_PRIVATE);
    }

    private static NotificationManager notifications(Context context) {
        return context.getSystemService(NotificationManager.class);
    }

    private static void createChannels(Context context, boolean english) {
        NotificationManager manager = notifications(context);
        NotificationChannel timer = new NotificationChannel(TIMER_CHANNEL, english ? "Focus timer" : "专注计时", NotificationManager.IMPORTANCE_LOW);
        timer.setSound(null, null);
        NotificationChannel alarm = new NotificationChannel(ALARM_CHANNEL, english ? "Break alarms" : "休息闹钟", NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(null, null);
        NotificationChannel fallback = new NotificationChannel(FALLBACK_ALARM_CHANNEL, english ? "Timer alarm fallback" : "计时到点提醒", NotificationManager.IMPORTANCE_HIGH);
        fallback.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        fallback.enableVibration(true);
        manager.createNotificationChannel(timer);
        manager.createNotificationChannel(alarm);
        manager.createNotificationChannel(fallback);
    }

    private static PendingIntent openApp(Context context) {
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 1, open, FLAGS);
    }

    static void update(Context context, JSONObject state) {
        SharedPreferences saved = preferences(context);
        long cursor = state.optLong("controlCursor");
        long sequence = saved.getLong("control-seq", 0);
        if (cursor < sequence && saved.contains("controls")) return;
        if (cursor != sequence) saved.edit().putLong("control-seq", cursor).apply();
        long endAt = state.optLong("endAt");
        if ("running".equals(state.optString("status")) && state.optBoolean("countdown")
                && endAt > 0 && endAt <= System.currentTimeMillis()) {
            handleDeadline(context, state, state.optString("id"), endAt, null);
        } else showTimer(context, state);
    }

    private static JSONObject readTimer(Context context) {
        if (latestTimer != null) return latestTimer;
        try { return new JSONObject(preferences(context).getString("timer-state", "{}")); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private static String clockText(long millis, boolean countingDown) {
        long seconds = (Math.max(0, millis) + (countingDown ? 999 : 0)) / 1000;
        return seconds >= 3600
            ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static void showTimer(Context context, JSONObject state) {
        latestTimer = state;
        String status = state.optString("status");
        String id = state.optString("id", "");
        boolean active = "running".equals(status) || "paused".equals(status);
        boolean running = "running".equals(status);
        boolean countdown = state.optBoolean("countdown");
        long endAt = state.optLong("endAt");
        boolean english = state.optBoolean("english");
        boolean finished = running && countdown && endAt <= System.currentTimeMillis();
        if (!active || id.isEmpty() || finished) {
            preferences(context).edit().putString("timer-state", state.toString()).apply();
            FloatingTimer.hide(context);
            cancelScheduled(context);
            notifications(context).cancel(TIMER_NOTIFICATION);
            return;
        }

        createChannels(context, english);
        long pausedTime = state.optLong(countdown ? "remaining" : "elapsed");
        String detail = running ? (english ? "Tap to open Yike" : "点击打开一刻")
            : (english ? "Paused · " : "已暂停 · ") + clockText(pausedTime, countdown);
        try { state.put("detail", detail); } catch (JSONException ignored) {}
        preferences(context).edit().putString("timer-state", state.toString()).apply();
        Intent control = new Intent(context, TimerReceiver.class).setAction(CONTROL)
            .putExtra("id", id).putExtra("pause", running).putExtra("when", state.optLong("when"));
        Notification.Builder notification = new Notification.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_status)
            .setContentTitle(state.optString("title"))
            .setContentText(detail)
            .setContentIntent(openApp(context))
            .addAction(new Notification.Action.Builder(null,
                running ? (english ? "Pause" : "暂停") : (english ? "Resume" : "继续"),
                PendingIntent.getBroadcast(context, running ? 4 : 5, control, FLAGS)).build())
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(running);
        if (running) {
            notification.setWhen(state.optLong("when", System.currentTimeMillis()))
                .setUsesChronometer(true).setChronometerCountDown(countdown);
        }
        notifications(context).notify(TIMER_NOTIFICATION, notification.build());
        updateFloating(context);
        if (running && countdown) schedule(context, id, endAt);
        else cancelScheduled(context);
    }

    static void setAppVisible(Context context, boolean visible) {
        appVisible = visible;
        if (latestTimer == null) latestTimer = readTimer(context);
        updateFloating(context);
    }

    static boolean isAppVisible() { return appVisible; }

    private static void updateFloating(Context context) {
        if (appVisible || latestTimer == null) FloatingTimer.hide(context);
        else FloatingTimer.update(context, latestTimer, openApp(context));
    }

    private static void schedule(Context context, String id, long endAt) {
        SharedPreferences saved = preferences(context);
        Intent due = new Intent(context, TimerReceiver.class).setAction(TIMER_DUE);
        if (id.equals(saved.getString("scheduled-id", "")) && endAt == saved.getLong("scheduled-end", 0)
            && PendingIntent.getBroadcast(context, 2, due, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE) != null) return;

        due.putExtra("id", id).putExtra("end", endAt);
        PendingIntent operation = PendingIntent.getBroadcast(context, 2, due, FLAGS);
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        saved.edit().putString("scheduled-id", id).putLong("scheduled-end", endAt).apply();
        try {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation);
        } catch (SecurityException unavailable) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, operation);
        }
    }

    private static void cancelScheduled(Context context) {
        SharedPreferences saved = preferences(context);
        if (!saved.contains("scheduled-id")) return;
        Intent due = new Intent(context, TimerReceiver.class).setAction(TIMER_DUE);
        PendingIntent operation = PendingIntent.getBroadcast(context, 2, due, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
        if (operation != null) context.getSystemService(AlarmManager.class).cancel(operation);
        saved.edit().remove("scheduled-id").apply();
    }

    static void onTimerDue(Context context, Intent intent, Runnable finish) {
        boolean playing = false;
        try {
            SharedPreferences saved = preferences(context);
            String id = intent.getStringExtra("id");
            long endAt = intent.getLongExtra("end", 0);
            if (id == null || !id.equals(saved.getString("scheduled-id", "")) || endAt != saved.getLong("scheduled-end", 0)) return;
            saved.edit().remove("scheduled-id").apply();
            if (endAt > System.currentTimeMillis()) {
                schedule(context, id, endAt);
                return;
            }
            playing = handleDeadline(context, readTimer(context), id, endAt, finish);
        } finally {
            if (!playing) finish.run();
        }
    }

    private static boolean handleDeadline(Context context, JSONObject state, String id, long endAt, Runnable finish) {
        if (!id.equals(state.optString("id")) || !"running".equals(state.optString("status")) || endAt != state.optLong("endAt")) return false;
        boolean rest = state.optBoolean("rest");
        boolean sound = state.optBoolean("sound", true);
        boolean chime = !rest && !"alarm".equals(state.optString("focusAlert")) && sound;
        try {
            if (rest || "alarm".equals(state.optString("focusAlert"))) {
                state.put("status", "waiting");
                showTimer(context, state);
                ring(context, id, endAt + 180_000L, sound, state.optBoolean("english"), !rest);
            } else {
                if ("rest".equals(state.optString("focusAfter"))) {
                    long duration = state.optLong("nextBreakDuration", 300_000);
                    long breakEnd = endAt + duration;
                    String breakId = id + "-break";
                    state.put("id", breakId).put("rest", true).put("duration", duration).put("remaining", duration)
                        .put("endAt", breakEnd).put("when", breakEnd).put("title", state.optString("nextBreakTitle"));
                    showTimer(context, state);
                    if (breakEnd <= System.currentTimeMillis()) handleDeadline(context, state, breakId, breakEnd, null);
                } else {
                    long duration = state.optLong("duration");
                    state.put("countdown", false).put("endAt", 0).put("when", endAt - duration)
                        .put("elapsed", duration).put("resumedAt", endAt);
                    showTimer(context, state);
                }
            }
        } catch (JSONException ignored) {}
        return chime && chime(context, id, endAt, finish);
    }

    static void control(Context context, Intent intent) {
        JSONObject state = readTimer(context);
        SharedPreferences saved = preferences(context);
        String id = intent.getStringExtra("id");
        boolean pause = intent.getBooleanExtra("pause", false);
        String expected = pause ? "running" : "paused";
        if (id == null || !id.equals(state.optString("id")) || !expected.equals(state.optString("status"))
            || intent.getLongExtra("when", -1) != state.optLong("when")) return;

        long now = System.currentTimeMillis();
        long endAt = state.optLong("endAt");
        boolean countdown = state.optBoolean("countdown");
        if (pause && countdown && endAt <= now) {
            handleDeadline(context, state, id, endAt, null);
            return;
        }
        try {
            long seq = saved.getLong("control-seq", 0) + 1;
            JSONArray controls = new JSONArray(saved.getString("controls", "[]"));
            controls.put(new JSONObject().put("seq", seq).put("id", id).put("at", now).put("pause", pause));
            long acknowledged = state.optLong("controlCursor");
            while (controls.length() > 64 && controls.getJSONObject(0).optLong("seq") <= acknowledged) controls.remove(0);
            if (pause && countdown) state.put("remaining", endAt - now).put("endAt", 0).put("when", 0);
            if (pause && !countdown) state.put("elapsed", Math.max(0, now - state.optLong("when"))).put("resumedAt", 0);
            if (!pause && countdown) state.put("endAt", now + state.optLong("remaining")).put("when", state.optLong("endAt"));
            if (!pause && !countdown) state.put("resumedAt", now).put("when", now - state.optLong("elapsed"));
            state.put("status", pause ? "paused" : "running");
            saved.edit().putLong("control-seq", seq).putString("controls", controls.toString()).apply();
            showTimer(context, state);
        } catch (JSONException ignored) {}
    }

    static void chime(Context context, String id, long dueAt) {
        chime(context, id, dueAt, null);
    }

    private static boolean chime(Context context, String id, long dueAt, Runnable finish) {
        SharedPreferences saved = preferences(context);
        if (id == null || id.isEmpty() || id.equals(saved.getString("last-chime", "")) || System.currentTimeMillis() - dueAt > CHIME_GRACE) return false;
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        MediaPlayer player = MediaPlayer.create(context, R.raw.focus_chime, attributes, 0);
        if (player == null) return false;
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable release = () -> {
            handler.removeCallbacksAndMessages(null);
            player.release();
            if (finish != null) finish.run();
        };
        player.setOnCompletionListener(completed -> release.run());
        player.setOnErrorListener((error, what, extra) -> { release.run(); return true; });
        try {
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            player.start();
            saved.edit().putString("last-chime", id).apply();
            handler.postDelayed(release, 4000);
            return true;
        } catch (IllegalStateException unavailable) {
            player.release();
            return false;
        }
    }

    static void ring(Context context, String id, long until, boolean sound, boolean english, boolean focus) {
        if (id == null || id.isEmpty() || until <= System.currentTimeMillis()) return;
        SharedPreferences saved = preferences(context);
        if (id.equals(saved.getString("last-alarm", "")) || id.equals(saved.getString("dismissed", ""))) return;
        saved.edit().putString("last-alarm", id).apply();
        createChannels(context, english);
        notifications(context).cancel(TIMER_NOTIFICATION);
        Intent start = new Intent(context, BreakAlarmService.class).putExtra("id", id)
            .putExtra("until", until).putExtra("sound", sound).putExtra("english", english).putExtra("focus", focus);
        try {
            context.startForegroundService(start);
        } catch (IllegalStateException | SecurityException unavailable) {
            String channel = sound ? FALLBACK_ALARM_CHANNEL : ALARM_CHANNEL;
            notifications(context).notify(ALARM_NOTIFICATION, alarmNotification(context, id, english, focus, channel));
        }
    }

    static Notification alarmNotification(Context context, String id, boolean english, boolean focus) {
        return alarmNotification(context, id, english, focus, ALARM_CHANNEL);
    }

    private static Notification alarmNotification(Context context, String id, boolean english, boolean focus, String channel) {
        Intent dismiss = new Intent(context, TimerReceiver.class).setAction(DISMISS).putExtra("id", id);
        return new Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_timer_status)
            .setContentTitle(focus ? (english ? "Focus goal reached" : "专注时间到") : (english ? "Break over" : "休息结束"))
            .setContentText(focus ? (english ? "Open Yike to choose what’s next" : "打开一刻选择下一步") : (english ? "Tap to see the alarm" : "点击查看闹钟"))
            .setContentIntent(openApp(context))
            .addAction(new Notification.Action.Builder(null, focus ? (english ? "Silence" : "停止铃声") : (english ? "Dismiss" : "关闭闹钟"),
                PendingIntent.getBroadcast(context, 3, dismiss, FLAGS)).build())
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build();
    }

    static void dismiss(Context context, String id) {
        if (id == null || id.isEmpty()) return;
        SharedPreferences saved = preferences(context);
        saved.edit().putString("dismissed", id).apply();
        if (!id.equals(saved.getString("last-alarm", ""))) return;
        context.stopService(new Intent(context, BreakAlarmService.class));
        notifications(context).cancel(ALARM_NOTIFICATION);
    }
}
