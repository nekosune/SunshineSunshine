/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherDigitalFace extends CanvasWatchFaceService {
    private static final String TAG = "WeatherDigitalFace";
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherDigitalFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mWeatherPaint;

        boolean mAmbient;
        Time mTime;
        String mWeather="Sunny";
        String mHigh="15C";
        String mLow="12C";

        float mXOffset;
        float mYOffset;
        float mYWeatherOffset;
        float mYTempOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherDigitalFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = WeatherDigitalFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mWeatherPaint= new Paint();
            mWeatherPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                Log.d(TAG,"Connecting to google API");
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
                mGoogleApiClient.disconnect();
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherDigitalFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherDigitalFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherDigitalFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);


            float weatherTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_weather_size_round : R.dimen.digital_weather_size);
            Rect bounds = new Rect();
            mTextPaint.getTextBounds("y",0,1,bounds);
            mYWeatherOffset=mYOffset+bounds.height()+5;
            mWeatherPaint.setTextSize(weatherTextSize);
            Rect weatherBounds = new Rect();
            mWeatherPaint.getTextBounds("y",0,1,weatherBounds);
            mYTempOffset=mYWeatherOffset+weatherBounds.height()+10;

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mWeatherPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
            canvas.drawText(mWeather,mXOffset,mYWeatherOffset,mWeatherPaint);
            if (getPeekCardPosition().isEmpty()) {
                canvas.drawText(String.format("%s/%s", mHigh, mLow), mXOffset, mYTempOffset, mWeatherPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            Log.d(TAG,"Adding Data Listener");
            updateWeather();
        }
        public static final String SUNSHINE_MESSAGE_PATH = "/sunshine_update";
        private void updateWeather() {
            Log.d(TAG,"Startng to send messges");
            Wearable.NodeApi.getLocalNode(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {
                @Override
                public void onResult(@NonNull NodeApi.GetLocalNodeResult getLocalNodeResult) {
                    Uri uri=new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WeatherFaceUtil.WEAR_DATA_PATH)
                            .authority(getLocalNodeResult.getNode().getId())
                            .build();
                    Log.d(TAG,uri.toString());
                    Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(new ResultCallback<DataItemBuffer>() {
                        @Override
                        public void onResult(@NonNull DataItemBuffer dataItems) {
                            if (dataItems.getCount() != 0) {
                                for(DataItem item:dataItems)
                                {
                                    String path=item.getUri().getPath();
                                    Log.d(TAG,"PAth: "+path);
                                    if(item.getUri().getPath().equals(WeatherFaceUtil.WEAR_DATA_PATH))
                                    {
                                        ChangeDetails(item);
                                    }
                                }
                            }

                            dataItems.release();
                        }
                    });
                    Wearable.DataApi.getDataItem(mGoogleApiClient,uri).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult res) {
                            if(res.getStatus().isSuccess())
                            {
                                if(res.getDataItem()!=null) {
                                    Log.d(TAG,res.getDataItem().toString());
                                    ChangeDetails(res.getDataItem());
                                }
                                else
                                {
                                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                        @Override
                                        public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                                            Log.d(TAG, "got Nodes");
                                            for (Node node : getConnectedNodesResult.getNodes()) {
                                                Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), SUNSHINE_MESSAGE_PATH, new byte[]{13, 37});
                                            }
                                        }
                                    });
                                }
                            }
                            else
                            {
                                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                                    @Override
                                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                                        Log.d(TAG, "got Nodes");
                                        for (Node node : getConnectedNodesResult.getNodes()) {
                                            Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), SUNSHINE_MESSAGE_PATH, new byte[]{13, 37});
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            });

        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG,"Got some data, parsing!");
            for(DataEvent dataEvent:dataEventBuffer)
            {
                Log.d(TAG,"Data Type is: " +dataEvent.getType());
                if(dataEvent.getType()!=DataEvent.TYPE_CHANGED)
                    continue;
                DataItem dataItem=dataEvent.getDataItem();
                Log.d(TAG,"Data Uri is: " +dataItem.getUri());
                Log.d(TAG,"Data Uri Path is : " +dataItem.getUri().getPath());
                if(!dataItem.getUri().getPath().equals(WeatherFaceUtil.WEAR_DATA_PATH))
                    continue;

                ChangeDetails(dataItem);
            }
        }

        private void ChangeDetails(DataItem dataItem) {
            DataMapItem dataMapItem=DataMapItem.fromDataItem(dataItem);
            DataMap config=dataMapItem.getDataMap();
            mHigh=config.getString(WeatherFaceUtil.WEAR_DATA_HIGH);
            mLow=config.getString(WeatherFaceUtil.WEAR_DATA_LOW);
            mWeather=WeatherFaceUtil.getStringForWeatherCondition(getApplicationContext(),config.getInt(WeatherFaceUtil.WEAR_DATA_TYPE));
            invalidate();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WeatherDigitalFace.Engine> mWeakReference;

        public EngineHandler(WeatherDigitalFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherDigitalFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
