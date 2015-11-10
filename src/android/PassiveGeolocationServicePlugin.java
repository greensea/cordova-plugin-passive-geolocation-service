package com.greensea.pgs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.os.Build;
import android.os.Bundle;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;


import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.greensea.pgs.constant.Constant;


public class PassiveGeolocationServicePlugin extends CordovaPlugin {

    private static final String TAG = PassiveGeolocationServicePlugin.class.getCanonicalName();
    public static final String PREFS_NAME = "PassiveGeolocationService";
    static SharedPreferences preferences;
    static SharedPreferences.Editor preferencesEditor;
    private Intent serviceIntent;
    
    private CallbackContext callbackContext;

    
    
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received location from bg service: " + intent);
            handleMessage(intent);
        }
    };

    @Override
    public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) throws JSONException {
        final Activity activity = this.cordova.getActivity();
        final Context context = activity.getApplicationContext();
        serviceIntent = new Intent(activity, PassiveGeolocationService.class);

        if (action.equals("start")) {
            Log.d(TAG, "Call `start` PassiveGeolocationService");
            
            IntentFilter intentFilter = new IntentFilter(Constant.LOCATION_BROADCAST);
            context.registerReceiver(mMessageReceiver, intentFilter);
            
            activity.startService(serviceIntent);
            callbackContext.success();

        } else if (action.equals("stop")) {
            Log.d(TAG, "Call `stop` PassiveGeolocationService");
            
            try {
                context.unregisterReceiver(mMessageReceiver);
            }
            catch (java.lang.IllegalArgumentException e) {
                Log.d(TAG, "PassiveGeolocationService may not started: " + e.toString());
            }
            
            activity.stopService(serviceIntent);
            callbackContext.success();
        } else if (action.equals("configure")) {
            try {
                this.callbackContext = callbackContext;
                
                Log.d(TAG, "Call `configure` PassiveGeolocationService");
                String jsonConfig = data.getString(0);
                Log.d(TAG, "Reconfigure service with: " + jsonConfig);
                JSONObject config = data.getJSONObject(0);
                preferences = context.getSharedPreferences(PREFS_NAME, 0 | Context.MODE_MULTI_PROCESS);
                preferencesEditor = preferences.edit();

                // startOnBoot
                if (config.has("startOnBoot")) {
                    Boolean startOnBoot = config.getBoolean("startOnBoot");
                    preferencesEditor.putBoolean("startOnBoot", startOnBoot);
                }
                // minTime, mininal interval between location updates, in milliseconds
                if (config.has("minTime")) {
                    Long minTime = config.getLong("minTime");
                    preferencesEditor.putLong("minTime", minTime);
                }
                // minDistance, mininal distance between location updates, in meters
                if (config.has("minDistance")) {
                    Double minDistance = config.getDouble("minDistance");
                    preferencesEditor.putFloat("minDistance", minDistance.floatValue());
                }
                // desiredAccuracy
                if (config.has("desiredAccuracy")) {
                    Double desiredAccuracy = config.getDouble("desiredAccuracy");
                    preferencesEditor.putFloat("desiredAccuracy", desiredAccuracy.floatValue());
                }
                // distanceFilter, mininal distance between location updates, in meters.
                if (config.has("distanceFilter")) {
                    Double distanceFilter = config.getDouble("distanceFilter");
                    preferencesEditor.putFloat("distanceFilter", distanceFilter.floatValue());
                }
                // debug, make sound on location updates
                if (config.has("debug")) {
                    Boolean isDebug = config.getBoolean("debug");
                    preferencesEditor.putBoolean("debug", isDebug);
                }
                
                if (config.has("minUploadInterval")) {
                    Long minUploadInterval = config.getLong("minUploadInterval");
                    preferencesEditor.putLong("minUploadInterval", minUploadInterval);
                }
                if (config.has("maxIdleTime")) {
                    Long maxIdleTime = config.getLong("maxIdleTime");
                    preferencesEditor.putLong("maxIdleTime", maxIdleTime);
                }
                if (config.has("appLocalUID")) {
                    String appLocalUID = config.getString("appLocalUID");
                    preferencesEditor.putString("appLocalUID", appLocalUID);
                }
                if (config.has("uploadOldByCell")) {
                    Boolean uploadOldByCell = config.getBoolean("uploadOldByCell");
                    preferencesEditor.putBoolean("uploadOldByCell", uploadOldByCell);
                }
                
                
                preferencesEditor.commit();
            } catch (JSONException e) {
                Log.w(TAG, "Invalig config object");
                e.printStackTrace();
                callbackContext.error("Invalid config object, make sure you put all require parameters in config object");
            }

        } else {
            return false;
        }

        return true;
    }
    
    private void handleMessage(Intent msg) {
        Bundle data = msg.getExtras();

        try {
            JSONObject location = new JSONObject(data.getString(Constant.DATA));
            
            PluginResult result = new PluginResult(PluginResult.Status.OK, location);
            result.setKeepCallback(true);
            this.callbackContext.sendPluginResult(result);
            
            Log.d(TAG, "Sending plugin result");
        } catch (JSONException e) {
            Log.w(TAG, "Error converting message to json");
        }
        
    }
}
