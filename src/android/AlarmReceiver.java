package com.greensea.pgs;

import android.content.BroadcastReceiver;  
import android.content.Context;  
import android.content.Intent;
import android.util.Log;


import com.greensea.pgs.constant.Constant;

  
public class AlarmReceiver extends BroadcastReceiver {  
    static final String TAG = PassiveGeolocationService.class.getCanonicalName();
    
    @Override  
    public void onReceive(Context context, Intent intent) {  
        if (intent.getAction().equals(Constant.START_SERVICE)) {  
            Log.i(TAG, "收到 Alarm 信号");
            Intent serviceIntent = new Intent(context, PassiveGeolocationService.class);
            context.startService(serviceIntent);
        }
    }  
}  
