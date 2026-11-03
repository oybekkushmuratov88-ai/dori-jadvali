package io.github.oybekkushmuratov88.dori;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Exact alarms for dose reminders, scheduled by the web app through the
 * DoriAndroid bridge. Each item is {at: epochMillis, title, body, nid}. Alarms
 * fire even when the app is closed; the schedule is saved so it survives a
 * reboot (rescheduled from AlarmReceiver on BOOT_COMPLETED).
 */
public class Reminders {
    static final String CHANNEL = "dose-reminders";
    static final String PREFS = "dori-reminders";
    static final String KEY_ITEMS = "items";
    static final String ACTION_FIRE = "io.github.oybekkushmuratov88.dori.FIRE";
    static final int MAX_ITEMS = 120;

    static AlarmManager am(Context c) { return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE); }
    static NotificationManager nm(Context c) { return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Dori eslatmalari", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Dori vaqti kelganda eslatadi");
        ch.enableVibration(true);
        nm(c).createNotificationChannel(ch);
    }

    /** Replace the whole schedule with the given JSON array of items. */
    static void schedule(Context c, String json) {
        ensureChannel(c);
        cancelAll(c);
        JSONArray items;
        try { items = new JSONArray(json == null ? "[]" : json); } catch (Exception e) { items = new JSONArray(); }

        long now = System.currentTimeMillis();
        JSONArray kept = new JSONArray();
        AlarmManager am = am(c);
        for (int i = 0; i < items.length() && kept.length() < MAX_ITEMS; i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            long at = it.optLong("at", 0);
            if (at <= now + 1000) continue;                 // skip past / imminent times
            int nid = it.optInt("nid", i + 1);
            PendingIntent pi = firePending(c, nid, it.optString("title", "Dori vaqti"), it.optString("body", ""));
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);        // exact not permitted -> inexact
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
                kept.put(it);
            } catch (SecurityException se) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                kept.put(it);
            }
        }
        prefs(c).edit().putString(KEY_ITEMS, kept.toString()).apply();
    }

    /** Re-arm the saved schedule (after a reboot), dropping times already past. */
    static void reschedule(Context c) {
        schedule(c, prefs(c).getString(KEY_ITEMS, "[]"));
    }

    static void cancelAll(Context c) {
        JSONArray items;
        try { items = new JSONArray(prefs(c).getString(KEY_ITEMS, "[]")); } catch (Exception e) { return; }
        AlarmManager am = am(c);
        for (int i = 0; i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it == null) continue;
            int nid = it.optInt("nid", i + 1);
            am.cancel(firePending(c, nid, "", ""));
        }
    }

    static PendingIntent firePending(Context c, int nid, String title, String body) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(ACTION_FIRE + ":" + nid)          // unique action so PendingIntents differ
                .putExtra("nid", nid)
                .putExtra("title", title)
                .putExtra("body", body);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, nid, i, flags);
    }

    /** Show a dose notification (called from AlarmReceiver when an alarm fires). */
    static void showNotification(Context c, int nid, String title, String body) {
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent tap = PendingIntent.getActivity(c, nid, open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(c, CHANNEL);
        else { b = new Notification.Builder(c); b.setPriority(Notification.PRIORITY_HIGH); }
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title == null || title.isEmpty() ? "Dori vaqti" : title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setWhen(System.currentTimeMillis());
        try { nm(c).notify(nid, b.build()); } catch (Exception e) {}
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
}
