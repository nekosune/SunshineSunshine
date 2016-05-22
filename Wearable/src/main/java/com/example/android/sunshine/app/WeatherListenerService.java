package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.google.android.gms.wearable.WearableListenerService;

public class WeatherListenerService extends WearableListenerService {

    private static final String WEAR_DATA_PATH="/sunshine";
    private static final String WEAR_DATA_HIGH="high";
    private static final String WEAR_DATA_LOW="low";
    private static final String WEAR_DATA_TYPE="type";

    public WeatherListenerService() {
    }

}
