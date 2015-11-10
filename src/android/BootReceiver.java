package com.greensea.pgs;

import android.app.AlarmManager;  
import android.util.Log;
import android.os.Environment;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;  


import com.greensea.pgs.constant.Constant;



public class BootReceiver extends BroadcastReceiver {
    static final String TAG = PassiveGeolocationService.class.getCanonicalName();
    static final String PREFS_NAME = "PassiveGeolocationService";
    SharedPreferences preferences;
    
    static Boolean alarmStarted = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        preferences = context.getSharedPreferences(PREFS_NAME, 0 | Context.MODE_MULTI_PROCESS);
        Boolean startOnBoot = preferences.getBoolean("startOnBoot", false);
        
        if (!startOnBoot) {
            Log.i(TAG, "onReceive, not start PassiveGeolocationService because startOnBoot=false");
            return;
        }
        
        
        Log.i(TAG, "onReceive, start PassiveGeolocationService because startOnBoot=true");
        
        Intent serviceIntent = new Intent(context, PassiveGeolocationService.class);
        context.startService(serviceIntent);
    }
    
    
    public static void startAlarm(Context context) {
        if (alarmStarted) {
            Log.i(TAG, "Alarm 已经设置过了，不会重新设置");
            return;
        }
        else {
            alarmStarted = true;
            Log.i(TAG, "设置定时启动 service 的 alarm");
        }
        
        /**
         * 为了保证服务不被意外停止，使用 Alarm 来保证服务持续运行，每 5 分钟发一次 alram
         */
        Intent intent = new Intent(context, AlarmReceiver.class);  
        intent.setAction(Constant.START_SERVICE);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);  
        
        long firstTime = SystemClock.elapsedRealtime();
        
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstTime, 5 * 60 * 1000, sender);
    }

}
