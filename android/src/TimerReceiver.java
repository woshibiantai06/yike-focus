package app.yike.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (TimerAlerts.TIMER_DUE.equals(intent.getAction())) {
            PendingResult pending = goAsync();
            TimerAlerts.onTimerDue(context.getApplicationContext(), intent, pending::finish);
        }
        else if (TimerAlerts.DISMISS.equals(intent.getAction())) TimerAlerts.dismiss(context, intent.getStringExtra("id"));
        else if (TimerAlerts.CONTROL.equals(intent.getAction())) TimerAlerts.control(context, intent);
    }
}
