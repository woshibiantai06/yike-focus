package app.yike.focus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class TimerAlerts {
    static final int ALARM_NOTIFICATION = 102;
    static final String TIMER_DUE = "app.yike.focus.TIMER_DUE";
    static final String DISMISS = "app.yike.focus.DISMISS_BREAK";
    private static final int TIMER_NOTIFICATION = 101;
    private static final String TIMER_CHANNEL = "timer";
    private static final String ALARM_CHANNEL = "break-alarm";
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
    private static JSONObject latestTimer;
    private static boolean appVisible;

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
        manager.createNotificationChannel(timer);
        manager.createNotificationChannel(alarm);
    }

    private static PendingIntent openApp(Context context) {
        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 1, open, FLAGS);
    }

    static void update(Context context, JSONObject state) {
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
            FloatingTimer.hide(context);
            cancelScheduled(context);
            notifications(context).cancel(TIMER_NOTIFICATION);
            return;
        }

        createChannels(context, english);
        Notification.Builder notification = new Notification.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_status)
            .setContentTitle(state.optString("title"))
            .setContentText(state.optString("detail"))
            .setContentIntent(openApp(context))
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
        if (running && countdown) schedule(context, state, id, endAt);
        else cancelScheduled(context);
    }

    static void setAppVisible(Context context, boolean visible) {
        appVisible = visible;
        updateFloating(context);
    }

    private static void updateFloating(Context context) {
        if (appVisible || latestTimer == null) FloatingTimer.hide(context);
        else FloatingTimer.update(context, latestTimer, openApp(context));
    }

    private static void schedule(Context context, JSONObject state, String id, long endAt) {
        SharedPreferences saved = preferences(context);
        boolean sound = state.optBoolean("sound", true);
        boolean english = state.optBoolean("english");
        Intent due = new Intent(context, TimerReceiver.class).setAction(TIMER_DUE);
        if (id.equals(saved.getString("scheduled-id", "")) && endAt == saved.getLong("scheduled-end", 0)
            && sound == saved.getBoolean("scheduled-sound", true) && english == saved.getBoolean("scheduled-en", false)
            && PendingIntent.getBroadcast(context, 2, due, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE) != null) return;

        due.putExtra("id", id).putExtra("end", endAt).putExtra("rest", state.optBoolean("rest"))
            .putExtra("sound", sound).putExtra("english", english);
        PendingIntent operation = PendingIntent.getBroadcast(context, 2, due, FLAGS);
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        saved.edit().putString("scheduled-id", id).putLong("scheduled-end", endAt)
            .putBoolean("scheduled-sound", sound).putBoolean("scheduled-en", english).apply();
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

    static void onTimerDue(Context context, Intent intent) {
        SharedPreferences saved = preferences(context);
        String id = intent.getStringExtra("id");
        long endAt = intent.getLongExtra("end", 0);
        if (id == null || !id.equals(saved.getString("scheduled-id", "")) || endAt != saved.getLong("scheduled-end", 0)) return;
        saved.edit().remove("scheduled-id").apply();
        notifications(context).cancel(TIMER_NOTIFICATION);
        FloatingTimer.hide(context);
        if (intent.getBooleanExtra("rest", false)) {
            ring(context, id, endAt + 180_000L, intent.getBooleanExtra("sound", true), intent.getBooleanExtra("english", false));
        }
    }

    static void ring(Context context, String id, long until, boolean sound, boolean english) {
        if (id == null || id.isEmpty() || until <= System.currentTimeMillis()) return;
        SharedPreferences saved = preferences(context);
        if (id.equals(saved.getString("last-alarm", "")) || id.equals(saved.getString("dismissed", ""))) return;
        saved.edit().putString("last-alarm", id).apply();
        createChannels(context, english);
        notifications(context).cancel(TIMER_NOTIFICATION);
        Intent start = new Intent(context, BreakAlarmService.class).putExtra("id", id)
            .putExtra("until", until).putExtra("sound", sound).putExtra("english", english);
        try {
            context.startForegroundService(start);
        } catch (IllegalStateException | SecurityException unavailable) {
            notifications(context).notify(ALARM_NOTIFICATION, alarmNotification(context, id, english));
        }
    }

    static Notification alarmNotification(Context context, String id, boolean english) {
        Intent dismiss = new Intent(context, TimerReceiver.class).setAction(DISMISS).putExtra("id", id);
        return new Notification.Builder(context, ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_timer_status)
            .setContentTitle(english ? "Break over" : "休息结束")
            .setContentText(english ? "Tap to see the alarm" : "点击查看闹钟")
            .setContentIntent(openApp(context))
            .addAction(new Notification.Action.Builder(null, english ? "Dismiss" : "关闭闹钟",
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
