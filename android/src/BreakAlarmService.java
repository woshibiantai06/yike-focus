package app.yike.focus;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.io.IOException;

public final class BreakAlarmService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String id = intent.getStringExtra("id");
        boolean english = intent.getBooleanExtra("english", false);
        startForeground(TimerAlerts.ALARM_NOTIFICATION, TimerAlerts.alarmNotification(this, id, english));
        stopRinging();
        long until = intent.getLongExtra("until", 0);
        long remaining = Math.min(180_000L, until - System.currentTimeMillis());
        if (remaining <= 0 || id == null || id.equals(TimerAlerts.preferences(this).getString("dismissed", ""))) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        AudioAttributes alarm = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] beat = { 180, 100, 180, 1440 };
            long[] pattern = new long[1 + 4 * (int) ((remaining + 1899) / 1900)];
            long timeLeft = remaining;
            for (int index = 1; index < pattern.length; index++) {
                pattern[index] = Math.min(timeLeft, beat[(index - 1) % beat.length]);
                timeLeft -= pattern[index];
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), alarm);
        }
        if (intent.getBooleanExtra("sound", true)) playAlarm(alarm, until - 180_000L);
        handler.postDelayed(() -> {
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }, remaining);
        return START_NOT_STICKY;
    }

    private void playAlarm(AudioAttributes attributes, long startedAt) {
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(attributes);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            try (android.content.res.AssetFileDescriptor sound = getResources().openRawResourceFd(R.raw.break_bell)) {
                player.setDataSource(sound.getFileDescriptor(), sound.getStartOffset(), sound.getLength());
            }
            player.setOnPreparedListener(ready -> {
                if (player != ready) return;
                ready.setLooping(true);
                ready.seekTo((int) (Math.max(0, System.currentTimeMillis() - startedAt) % 1900));
                ready.start();
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException unavailable) {
            player.release();
            player = null;
        }
    }

    private void stopRinging() {
        handler.removeCallbacksAndMessages(null);
        if (vibrator != null) vibrator.cancel();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        stopRinging();
        super.onDestroy();
    }
}
