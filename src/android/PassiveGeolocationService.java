package com.greensea.pgs;

import org.json.*;
import java.util.*;

import android.util.Log;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Environment;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.app.Service;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.widget.Toast;

import android.app.Notification;


import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.media.AudioManager;
import android.media.ToneGenerator;

import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;

import android.database.sqlite.*;
import android.database.Cursor;
import android.content.ContentValues;
import com.loopj.android.http.*;
import org.apache.http.Header;


import com.greensea.pgs.constant.Constant;


public class PassiveGeolocationService extends Service implements LocationListener {

    static final String TAG = PassiveGeolocationService.class.getCanonicalName();
    static final String PREFS_NAME = "PassiveGeolocationService";

    protected ToneGenerator toneGenerator;
    
    Boolean isDebug;
    Long minTime;
    Float minDistance;
    Float desiredAccuracy;
    Float distanceFilter;
    Long minUploadInterval;
    Long lastUploadTime;
    String appLocalUID;
    Boolean uploadOldByCell;
    Long maxIdleTime;
    String apiURL;
    

    Location lastLocation = null;

    int currentNetworkType = ConnectivityManager.TYPE_DUMMY;
    int lastNetworkType = ConnectivityManager.TYPE_DUMMY;
    String currentNetworkTypeName = "DUMMY";
    String lastNetworkTypeName = "DUMMY";

    SharedPreferences pref;
    LocationManager locationManager;
    
    static SQLiteDatabase db = null;





    @Override
    public void onCreate() {      
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
                
                
        BootReceiver.startAlarm(this);
        
                
        /// 初始化配置信息
        pref = this.getSharedPreferences(PREFS_NAME, 0 | Context.MODE_MULTI_PROCESS);

        minTime = pref.getLong("minTime", 0);
        minDistance = pref.getFloat("minDistance", 10);
        desiredAccuracy = pref.getFloat("desiredAccuracy", 100);
        distanceFilter = pref.getFloat("distanceFilter", 0);
        isDebug = pref.getBoolean("debug", false);
        minUploadInterval = pref.getLong("minUploadInterval", 5 * 60 * 1000);
        appLocalUID = pref.getString("appLocalUID", "");
        uploadOldByCell = pref.getBoolean("uploadOldByCell", false);
        maxIdleTime = pref.getLong("maxIdleTime", 10 * 60 * 1000);
        
        apiURL = "https://latitude.greensea.org:4433/api/v3/log.php?uid=" + appLocalUID;
    
        
        /// 初始化内部变量
        lastUploadTime = 0L; 
        
        
        /// 设置网络状态监听
        IntentFilter filter = new IntentFilter(); 
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkChangedReceiver, filter);
        
        
        /// 设置位置监听
        Log.i(TAG, "onCreate,  desiredAccuracy=" + desiredAccuracy + ", minDistance=" + minDistance + ", minTime=" + minTime + ", distanceFilter=" + distanceFilter + ", isDebug=" + isDebug);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        // Android before JellyBean may ignore minTime and minDistance
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, minTime, minDistance, this);
    }

    public void locationHandler(Location location) {        
        Log.d(TAG, "locationHandler fired, location=" + location + ", lastLocation=" + lastLocation);

        
        final long locationTime = location.getTime();
        final double longitude = location.getLongitude();
        final double latitude = location.getLatitude();
        final float accuracy = location.getAccuracy();
        
        
        Long currentTime = location.getTime();
        Long lastTime = 0L;
        if (lastLocation != null) {
            lastTime = lastLocation.getTime();
        }
        Long timeElapsed = currentTime - lastTime;
        
        
        Boolean shouldSave = true;
        do {
            /// 是否至少应该上报一次位置了？(maxIdleTime)
            if (timeElapsed >= maxIdleTime) {
                if (isDebug) {
                    Toast.makeText(this, "force update (timeElapsed)" + timeElapsed + ">(maxIdleTime)" + maxIdleTime, Toast.LENGTH_LONG).show();
                    startTone("chirp_chirp_chirp");
                }
                
                Log.d(TAG, "本次定位距离上次定位已经过去 " + (timeElapsed / 1000) + " 秒(>=" + (maxIdleTime / 1000) + ")，应进行定位");
                shouldSave = true;
                break;
            }
            else {
                Log.d(TAG, "本次定位距离上次定位已经过去 " + (timeElapsed / 1000) + " 秒(<" + (maxIdleTime / 1000) + "),继续判断");
            }
            
            
            /// Is accuracy < desiredAccuracy
            if (desiredAccuracy > 0 && accuracy > desiredAccuracy) {
                if (isDebug) {
                    Toast.makeText(this, "no acy, (loc)" + accuracy + ">(desired)" + desiredAccuracy, Toast.LENGTH_LONG).show();
                    startTone("doodly_doo");
                }
                
                Log.d(TAG, "locationHandler, reject by desiredAccuracy: (loc)" + accuracy + ">(desired)" + desiredAccuracy);
                
                shouldSave = false;
                break;
            }
            
            
            
            /// Is distance > distanceFilter            
            if (lastLocation != null) {
                float distance = lastLocation.distanceTo(location);
                float lastAccuracy = lastLocation.getAccuracy();
                
                Log.d(TAG, "locationHandler, distance to lastLocation is " + distance);
                
                if (distance <= distanceFilter) {
                    if (isDebug) {
                        Toast.makeText(this, "dis filter, (loc)" + distance + "<=(filter)" + distanceFilter + ", // (loc acy)" + accuracy + ">=(last acy)" + lastAccuracy, Toast.LENGTH_LONG).show();
                        startTone("long_beep");
                    }
                    
                    Log.d(TAG, "locationHandler, reject by distanceFilter: (loc)" + distance + "<=(filter)" + distanceFilter + ", // (loc acy)" + accuracy + ">=(last acy)" + lastAccuracy);
                    
                    shouldSave = false;
                    break;
                }
            }
        }
        while (false);
        
        

        if (shouldSave == true) {
            Log.d(TAG, "根据一系列判断的结果，该位置应该被记录: " + location.toString());
            
            if (isDebug) {
                startTone("beep");
                Toast.makeText(this, "located: acy=" + accuracy + ", lat=" + latitude + ", log=" + longitude, Toast.LENGTH_LONG).show();
            }
            
            lastLocation = location;
            saveLocation(location);
        }
        else {
            Log.d(TAG, "根据一系列判断的结果，该位置不应该被记录，抛弃该记录: " + location.toString());
            
            /*
            if (isDebug) {
                startTone("long_beep");
            }
            */
        }
    }



    /**
     * 将位置信息上传到服务器
     */
    protected void saveLocation(Location loc) {
        Location[] locs = {loc};
        
        saveLocations(locs);
    }
     
    protected void saveLocations(Location[] locs) {
        Long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastUploadTime < minUploadInterval) {
            Log.d(TAG, "尚未达到最小上传间隔限制（剩余 " + ((minUploadInterval - (currentTime - lastUploadTime)) / 1000) + "秒），本次位置暂不上传，保存到数据库中: " + locs.toString());
            storeLocations(locs);
            return;
        }
        
        
        /// 判断是否要上传旧位置
        Location newLocs[];
        Boolean wifiOn = isWiFi();
        if (uploadOldByCell || wifiOn) {
            Log.d(TAG, "顺便上传旧的位置, uploadOldByCell=" + uploadOldByCell + ", WiFi on = " + wifiOn);
            Location oldLocs[] = fetchLatestLocations(10);
            
            newLocs = new Location[oldLocs.length + locs.length];
            for (int i = 0; i < oldLocs.length; i++) {
                newLocs[i] = oldLocs[i];
            };
            
            for (int i = oldLocs.length, k = 0; i < oldLocs.length + locs.length; i++, k++) {
                newLocs[i] = locs[k];
            }
        }
        else {
            newLocs = locs;
        }
            
        
        Log.d(TAG, "将 " + newLocs.length + " 个位置信息上传到服务器: " + newLocs.toString());
        uploadLocations(newLocs);
    }
    
    
    
    
    protected void uploadLocations(final Location[] locs) {
        AsyncHttpClient httpClient;
        
        httpClient = new AsyncHttpClient();
        httpClient.setMaxRetriesAndTimeout(1, 30 * 1000);
        httpClient.setTimeout(30 * 1000);
        
        
        
        RequestParams params = new RequestParams();
        params.put("locations", locs2JSONStr(locs));
        
        final String requestMsg = String.format(
            "http request url: %s, with params: %s",
            apiURL, params.toString()
        );
        
        lastUploadTime = System.currentTimeMillis();

        httpClient.post(apiURL, params, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                Log.d(TAG, "httpClient.post.onStart, " + requestMsg);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                Log.d(TAG, "httpClient.post.onSuccess");
                
                
                try {
                    String stringResponse = new String(response, "UTF-8");
                    if (stringResponse.length() == 0) {
                        Log.w(TAG, "服务器返回了空内容，忽略之");
                        return;
                    }
                    
                    if (stringResponse.compareTo("ok") == 0) {
                        Log.d(TAG, "成功上传了 " + locs.length + " 个位置信息");
                    }
                    else {
                        /// 操作失败
                        Log.i(TAG, "上传 + " + locs.length + " 个位置到服务器失败，保存位置信息到本地数据库，服务器返回：`" + stringResponse + "'");
                        storeLocations(locs);
                    }
                }
                catch (java.io.UnsupportedEncodingException ex) {
                    storeLocations(locs);
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] error, Throwable ex) {
                String err = "";
                try {
                    err = new String(error, "UTF-8");
                }
                catch (java.io.UnsupportedEncodingException ex2) {
                    err = error.toString();
                }
                catch (java.lang.NullPointerException e2) {
                    err = "error 内容为空";
                }
                Log.d(TAG, "(onFailure by LocationUpdate)上传位置失败，将位置信息保存到数据库中: " + ex);
                
                storeLocations(locs);
            }

            @Override
            public void onRetry(int retryNo) {
                Log.w(TAG, "httpClient.post.onRetry, http request url: " + requestMsg);
            }
        });
    }
    
    
    protected SQLiteDatabase getDB() {
        
        try {
            if (db == null) {
                String dbPath = this.getDir("", Context.MODE_PRIVATE).toString() + "/locs.sqlite";
                
                Log.d(TAG, "使用 `" + dbPath + "' 作为数据库地址");
                
                db = SQLiteDatabase.openOrCreateDatabase(dbPath, null); 
                db.execSQL("CREATE TABLE IF NOT EXISTS `b_location` (" +
        "`location_id`	INTEGER PRIMARY KEY AUTOINCREMENT," +
        "`latitude`	TEXT," +
        "`longitude`	TEXT," +
        "`altitude`	TEXT," +
        "`accuracy`	TEXT," +
        "`rtime`	BIGINT," +
        "`ctime`	BIGINT, " +
        "`src`	TEXT " + 
    ");  " + 
    "CREATE INDEX idx_b_location_rtime IF NOT EXISTS ON b_location(rtime); "
                );
            }
        }
        catch (java.lang.IllegalStateException e) {
            Log.w(TAG, "打开数据库时发生了一个错误: " + e);
        }
        
        
        
        return db;
    }
    
    
    /**
     * 保存位置信息到数据库中
     */
    protected void storeLocations(Location[] locs) {
        long ret;
        
        for (int i = 0; i < locs.length; i++) {
            ContentValues data = loc2ContentValues(locs[i]);
            data.put("ctime", System.currentTimeMillis());
            
            ret = getDB().insertOrThrow("b_location", null, data);
            if (ret == -1) {
                Log.i(TAG, "无法保存位置信息到数据库中: " + locs[i].toString());
            }
            else {
                Log.i(TAG, "向数据库中保存了一个位置，记录编号是 " + ret + ", 位置内容: " + locs[i].toString());
            }
        }
        
        Log.i(TAG, "将 " + locs.length + " 个位置信息保存到了数据库中");
    }
    
    
    
    protected ContentValues loc2ContentValues(Location loc) {
        ContentValues ret = new ContentValues();
        
        Double latitude = loc.getLatitude();
        Double longitude = loc.getLongitude();
        Double altitude = loc.getAltitude();
        Float accuracy = loc.getAccuracy();
        
        
        
        ret.put("latitude", latitude.toString());
        ret.put("longitude", longitude.toString());
        ret.put("altitude", altitude.toString());
        ret.put("accuracy", accuracy.toString());
        ret.put("rtime", loc.getTime());
        ret.put("src", loc.getProvider());
        
        return ret;
    }
    
    
    protected Location[] fetchLatestLocations(int num) {
        Cursor cur;
        SQLiteDatabase db;
        int ret;
        
        db = getDB();
        
        String cols[] = {"location_id", "latitude", "longitude", "altitude", "accuracy", "rtime", "src"};
        String selection = null;
        String selectionArgs[] = null;
        String groupBy = null;
        String having = null;
        String orderBy = "rtime DESC";
        String limit = String.format("%d", num);
        
        cur = db.query("b_location", cols, selection, selectionArgs, groupBy, having, orderBy, limit);
        
        Log.d(TAG, String.format("从数据库中取 %d 个记录", cur.getCount()));
        
        Location[] locs = new Location[cur.getCount()];
        int ids[] = new int[cur.getCount()];
        
        cur.moveToFirst();
        for (int i = 0; i < cur.getCount(); i++) {
            Log.d(TAG, "读取第 " + i + " 条记录");
            locs[i] = new Location(cur.getString(cur.getColumnIndex("src")));
            locs[i].setLatitude(Double.parseDouble(cur.getString(cur.getColumnIndex("latitude"))));
            locs[i].setLongitude(Double.parseDouble(cur.getString(cur.getColumnIndex("longitude"))));
            locs[i].setAltitude(Double.parseDouble(cur.getString(cur.getColumnIndex("altitude"))));
            locs[i].setAccuracy(Float.parseFloat(cur.getString(cur.getColumnIndex("accuracy"))));
            locs[i].setTime(cur.getLong(cur.getColumnIndex("rtime")));
            
            ids[i] = cur.getInt(cur.getColumnIndex("location_id"));
            
            cur.moveToNext();
        }
        
        cur.close();
        
        
        /// 删除数据库中的
        for (int i = 0; i < ids.length; i++) {
            ret = db.delete("b_location", String.format("location_id=%d", ids[i]), null);
            Log.d(TAG, "数据库删除结果，location_id=" + ids[i] + ", 影响了 " + ret + " 行");
        }
        
        return locs;
    }
    
    
    
    protected String locs2JSONStr(Location[] locs) {
        String ret;
        
        if (locs.length <= 0) {
            return "[]";
        }
        
        String token[] = new String[locs.length];
        for (int i = 0; i < locs.length; i++) {
            Double latitude = locs[i].getLatitude();
            Double longitude = locs[i].getLongitude();
            Double altitude = locs[i].getAltitude();
            Float accuracy = locs[i].getAccuracy();
            
            token[i] = String.format("{\"latitude\": \"%s\", \"longitude\": \"%s\", \"altitude\": \"%s\", \"accuracy\": \"%s\", \"src\": \"%s\", \"time\": %d}", latitude.toString(), longitude.toString(), altitude.toString(), accuracy.toString(), locs[i].getProvider(), locs[i].getTime());
        }
        
        
        /// 妈蛋！连个 join() 方法都没有，还要自己造一个（如果是我孤陋寡闻的话请把这段代码改掉吧）
        ret = "[ ";
        for (int i = 0; i < token.length - 1; i++) {
            ret += token[i];
            ret += ", ";
        }
        ret += token[token.length - 1];
        ret += " ]";
        

        Log.d(TAG, "格式化后的 JSON Location 字串: " + ret);
        
        return ret;
    }
    
    
    /**
     * 当 WiFi 可用时，调用此方法上传旧的位置信息
     */
    protected void uploadOldLocationsByWiFi() {
        if (!isWiFi()) {
            Log.d(TAG, "当前网络不是 WiFi，停止上传旧位置信息的操作");
            return;
        }
        else {
            Log.d(TAG, "当前网络是 WiFi，可以上传旧的位置信息");
        }
        
        
        final Location locs[] = fetchLatestLocations(50);
        if (locs.length <= 0) {
            Log.d(TAG, "数据库中没有旧的数据了，停止通过 WiFi 上传旧位置信息");
            return;
        }
        
        
        /// 开始上传
        /// FIXME: 我们需要加一个锁：同一时刻只能有一个　uploadOldLocationsByWiFi() 在运行。
        /// FIXME: 这是为了防止网络状态频繁变化而导致启动多次 uploadOldLocationsByWiFi()，造成后台有多个 AsyncHttpResponseHandler 在运行
        AsyncHttpClient httpClient;
        
        httpClient = new AsyncHttpClient();
        httpClient.setMaxRetriesAndTimeout(1, 30 * 1000);
        httpClient.setConnectTimeout(30 * 1000);
        
        
        
        RequestParams params = new RequestParams();
        params.put("locations", locs2JSONStr(locs));
        
        final String requestMsg = String.format(
            "http request url: %s, with params: %s",
            apiURL, params.toString()
        );
    
        httpClient.post(apiURL, params, new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                Log.d(TAG, "httpClient.post.onStart, " + requestMsg);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                Log.d(TAG, "httpClient.post.onSuccess");
                
                
                try {
                    String stringResponse = new String(response, "UTF-8");
                    if (stringResponse.length() == 0) {
                        Log.w(TAG, "服务器返回了空内容，忽略之");
                        return;
                    }
                    
                    if (stringResponse.compareTo("ok") == 0) {
                        Log.d(TAG, "成功上传了 " + locs.length + " 个旧的位置信息");
                        uploadOldLocationsByWiFi();
                    }
                    else {
                        /// 操作失败
                        Log.i(TAG, "上传 + " + locs.length + " 个旧的位置到服务器失败，保存位置信息到本地数据库，服务器返回：`" + stringResponse + "'");
                        storeLocations(locs);
                    }
                }
                catch (java.io.UnsupportedEncodingException ex) {
                    storeLocations(locs);
                    //ex.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] error, Throwable ex) {
                String errstr;
                try {
                    errstr = new String(error, "UTF-8");
                }
                catch (java.io.UnsupportedEncodingException e) {
                    errstr = "(无法解析 error 的内容)";
                }
                catch (java.lang.NullPointerException e2) {
                    errstr = "error 内容为空";
                }
                Log.d(TAG, "httpClient.post.onFailure (byWiFi)");
                Log.d(TAG, "(onFailure, byWiFi)上传旧的位置失败，将位置信息保存到数据库中，错误信息: " + ex);
                
                storeLocations(locs);
            }

            @Override
            public void onRetry(int retryNo) {
                Log.w(TAG, "httpClient.post.onRetry, http request url: " + requestMsg);
            }
        });
    

    }
    


	public static JSONObject toJSONObject(Location location) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("time", location.getTime());
		json.put("latitude", location.getLatitude());
		json.put("longitude", location.getLongitude());
		json.put("accuracy", location.getAccuracy());
		json.put("speed", location.getSpeed());
		json.put("altitude", location.getAltitude());
		json.put("bearing", location.getBearing());
		return json;
	}


    protected boolean isWiFi() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = cm.getActiveNetworkInfo();
        if (activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        else {
            return false;
        }
    }
    

    private static boolean isWifi(Context mContext) {
		ConnectivityManager connectivityManager = (ConnectivityManager) mContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
		if (activeNetInfo != null
				&& activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
			return true;
		}
		return false;
	}


    protected void startTone(String name) {
        int tone = 0;
        int duration = 1000;

        if (name.equals("beep")) {
            tone = ToneGenerator.TONE_PROP_BEEP;
        } else if (name.equals("beep_beep_beep")) {
            tone = ToneGenerator.TONE_CDMA_CONFIRM;
        } else if (name.equals("long_beep")) {
            tone = ToneGenerator.TONE_CDMA_ABBR_ALERT;
        } else if (name.equals("doodly_doo")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE;
        } else if (name.equals("chirp_chirp_chirp")) {
            tone = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD;
        } else if (name.equals("dialtone")) {
            tone = ToneGenerator.TONE_SUP_RINGTONE;
        }
        toneGenerator.startTone(tone, duration);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            locationHandler(location);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        
        if (db != null) {
            //db.close();
            //db = null;
        }
        
        unregisterReceiver(mNetworkChangedReceiver);
        
        locationManager.removeUpdates(this);
        
        super.onDestroy();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.v(TAG, "onProviderDisabled, disabled: " + provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.v(TAG, "onProviderEnabled, enabled: " + provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.v(TAG, String.format(
            "onStatusChanged, provider: %s status: %i extars: %s",
            provider, status, extras)
        );
    }
    
    
    private BroadcastReceiver mNetworkChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager;
            NetworkInfo info;
            String action = intent.getAction();
            
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                info = connectivityManager.getActiveNetworkInfo();  
                
                lastNetworkType = currentNetworkType;
                lastNetworkTypeName = currentNetworkTypeName;
                if (info == null) {
                    currentNetworkType = ConnectivityManager.TYPE_DUMMY;
                    currentNetworkTypeName = "DUMMY";
                }
                else {
                    currentNetworkType = info.getType();
                    currentNetworkTypeName = info.getTypeName();
                }
                
                
                Log.d(TAG, "网络连接从 " + lastNetworkTypeName + " 变为 " + currentNetworkTypeName);
                
                if (lastNetworkType != ConnectivityManager.TYPE_WIFI && currentNetworkType == ConnectivityManager.TYPE_WIFI) {
                    Log.d(TAG, "WiFi 可用，开始上传旧的位置信息");
                    uploadOldLocationsByWiFi();
                }
            }
        }
    };
}
