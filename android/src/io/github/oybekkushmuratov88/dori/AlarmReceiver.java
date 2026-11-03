package io.github.oybekkushmuratov88.dori;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires a dose notification when an alarm goes off, and re-arms the schedule after a reboot. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            Reminders.reschedule(context);
            return;
        }
        int nid = intent.getIntExtra("nid", 1);
        Reminders.showNotification(context, nid, intent.getStringExtra("title"), intent.getStringExtra("body"));
    }
}
