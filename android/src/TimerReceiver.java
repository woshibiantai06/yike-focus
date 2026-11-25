package app.yike.focus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (TimerAlerts.TIMER_DUE.equals(intent.getAction())) TimerAlerts.onTimerDue(context, intent);
        else if (TimerAlerts.DISMISS.equals(intent.getAction())) TimerAlerts.dismiss(context, intent.getStringExtra("id"));
        else if (TimerAlerts.CONTROL.equals(intent.getAction())) TimerAlerts.control(context, intent);
    }
}
