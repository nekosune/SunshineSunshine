package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

public class ListenForWatch extends WearableListenerService {
    public static final String SUNSHINE_MESSAGE_PATH = "/sunshine_update";
    public static final byte[] MAGIC_BYTES=new byte[]{13,37};
    private static final String WEAR_DATA_PATH="/sunshine";
    private static final String WEAR_DATA_HIGH="high";
    private static final String WEAR_DATA_LOW="low";
    private static final String WEAR_DATA_TYPE="type";
    public GoogleApiClient mGoogleApi;
    public ListenForWatch() {

    }


    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( messageEvent.getPath().equalsIgnoreCase( SUNSHINE_MESSAGE_PATH ) ) {
            if(mGoogleApi==null)
            {
                mGoogleApi=new GoogleApiClient.Builder(getApplicationContext()).addApi(Wearable.API).build();
            }
            Log.d("ListenForWatch","Got the message to send weather back!");
                Log.d("ListenForWatch","Updating");
                updateWear(mGoogleApi,getApplicationContext());
        }
    }

    public static void updateWear(GoogleApiClient mApiClient,Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String displayNotificationsKey = context.getString(R.string.pref_enable_notifications_key);
        String lastNotificationKey = context.getString(R.string.pref_last_notification);

        // Last sync was more than 1 day ago, let's send a notification with the weather.
        String locationQuery = Utility.getPreferredLocation(context);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = context.getContentResolver().query(weatherUri, SunshineSyncAdapter.NOTIFY_WEATHER_PROJECTION, null, null, null);
        Log.d("ListenForWatch","SendingData");
        if (cursor.moveToFirst()) {
            Log.d("ListenForWatch","GotDetails");
            int weatherId = cursor.getInt(SunshineSyncAdapter.INDEX_WEATHER_ID);
            double high = cursor.getDouble(SunshineSyncAdapter.INDEX_MAX_TEMP);
            double low = cursor.getDouble(SunshineSyncAdapter.INDEX_MIN_TEMP);
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEAR_DATA_PATH);
            putDataMapRequest.getDataMap().putInt(WEAR_DATA_TYPE, weatherId);
            putDataMapRequest.getDataMap().putString(WEAR_DATA_HIGH, Utility.formatTemperature(context, high));
            putDataMapRequest.getDataMap().putString(WEAR_DATA_LOW, Utility.formatTemperature(context, low));
            PutDataRequest putRequest = putDataMapRequest.asPutDataRequest();
            if(!mApiClient.isConnected()) {
                mApiClient.blockingConnect(5, TimeUnit.SECONDS);
            }
            if (!mApiClient.isConnected()) {
                return;
            }
            Log.d("ListenForWatch","Sending");
            DataApi.DataItemResult res=Wearable.DataApi.putDataItem(mApiClient, putRequest).await();
            Log.d("ListenForWatch","GotResult: "+res.getStatus());
        }
    }
}
