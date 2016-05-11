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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
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
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
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

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        private static final String REQUEST_WEATHER_PATH = "/weather_initial_request";
        boolean mRegisteredTimeZoneReceiver = false;
        Bitmap mBackgroundBitmap;
        Bitmap mIconBitmap;
        String mMinTemp = "";
        String mMaxTemp = "";
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        boolean mAmbient;
        Time mTime;
        int mTapCount;
        float mXOffset;
        float mYOffset;
        float mHighTempXOffset;
        float mLowTempXOffset;
        int id;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        GoogleApiClient mGoogleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
        //Connect to Google API
            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addOnConnectionFailedListener(this)
                    .addConnectionCallbacks(this)
                    .build();
            if (mGoogleApiClient == null) {
                return;
            }
            mGoogleApiClient.connect();


            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = MyWatchFace.this.getResources();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(getColor(R.color.digital_text));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(getColor(R.color.white));

            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(getColor(R.color.white));

            mTime = new Time();


        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            //Disconnect for the Google Api Client
            mGoogleApiClient.disconnect();
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
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);

            mHighTempXOffset = resources.getDimension(isRound
                    ? R.dimen.maxTemp_x_offset_round : R.dimen.maxTemp_x_offset);
            mLowTempXOffset = resources.getDimension(isRound
                    ? R.dimen.minTemp_x_offset_round : R.dimen.minTemp_x_offset);
            float maxTempTextSize = resources.getDimension(isRound
                    ? R.dimen.maxTemp_text_size_round : R.dimen.maxTemp_text_size);
            float minTempTextSize = resources.getDimension(isRound
                    ? R.dimen.minTemp_text_size_round : R.dimen.minTemp_text_size);
            mHighTempPaint.setTextSize(maxTempTextSize);
            mLowTempPaint.setTextSize(minTempTextSize);
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
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        public int getArtResourceForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.art_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.art_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.art_rain;
            } else if (weatherId == 511) {
                return R.drawable.art_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.art_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.art_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.art_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.art_storm;
            } else if (weatherId == 800) {
                return R.drawable.art_clear;
            } else if (weatherId == 801) {
                return R.drawable.art_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.art_clouds;
            }
            return -1;
        }

        public int getBackgroundResourceForWeather(int weatherId) {
            // Update background color for weather
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.dark_grey_bcgrd;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.dark_grey_bcgrd;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.dark_grey_bcgrd;
            } else if (weatherId == 511) {
                return R.drawable.dark_grey_bcgrd;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.dark_grey_bcgrd;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.light_grey_bcgrd;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.light_grey_bcgrd;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.light_grey_bcgrd;
            } else if (weatherId == 800) {
                return R.drawable.sky_blue_bcgrd;
            } else if (weatherId == 801) {
                return R.drawable.sky_blue_bcgrd;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.grey_bcgrd;
            }
            return -1;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            Resources resources = MyWatchFace.this.getResources();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MyWatchFace.this);
            id = prefs.getInt("id", 0);
            mMaxTemp = prefs.getString("max", "NA");
            mMinTemp = prefs.getString("min", "NA");
            Log.d("TAG11", mMinTemp);


            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                //set the position of the weather text and time while in ambient mode
                canvas.drawText(mMaxTemp,
                        mHighTempXOffset,
                        resources.getDimension(R.dimen.maxTemp_text_position_y) ,
                        mHighTempPaint);
                canvas.drawText(mMinTemp,
                        mLowTempXOffset, resources.getDimension(R.dimen.minTemp_text_position_y),
                        mLowTempPaint);

                mTime.setToNow();
                String text = mAmbient
                        ? String.format("%d:%02d", mTime.hour, mTime.minute)
                        : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
                canvas.drawText(text, mXOffset, resources.getDimension(R.dimen.time_x_offset), mTextPaint);

            } else {

                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), getBackgroundResourceForWeather(id));
                if (mBackgroundBitmap != null) {
                    canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
                }
                    Bitmap mIconBitmap = BitmapFactory.decodeResource(getResources(), getArtResourceForWeatherCondition(id));
                    //This commented out code below is not necessary because I have implemented changing backgrounds based on the weather
                    //canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

                    if (mIconBitmap != null) {

                        canvas.drawBitmap(mIconBitmap, resources.getDimension(R.dimen.weather_icon_x_offset) , resources.getDimension(R.dimen.weather_icon_y_offset), mBackgroundPaint);

                    }
                    //set the position of the weather text and time
                    canvas.drawText(mMaxTemp,
                            mHighTempXOffset,
                            resources.getDimension(R.dimen.maxTemp_text_position_y) ,
                            mHighTempPaint);
                    canvas.drawText(mMinTemp,
                            mLowTempXOffset, resources.getDimension(R.dimen.minTemp_text_position_y),
                            mLowTempPaint);

                    mTime.setToNow();
                    String text = mAmbient
                            ? String.format("%d:%02d", mTime.hour, mTime.minute)
                            : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
                    canvas.drawText(text, mXOffset, resources.getDimension(R.dimen.time_x_offset), mTextPaint);

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

        private void requestInitialData() {

            PutDataMapRequest request = PutDataMapRequest.create(REQUEST_WEATHER_PATH).setUrgent();
            DataMap dataMap = request.getDataMap();
            dataMap.putLong("time", System.currentTimeMillis());
            Wearable.DataApi.putDataItem(mGoogleApiClient, request.asPutDataRequest())
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {

                            } else {
                            }
                        }
                    });
        }


        @Override
        public void onConnected(Bundle bundle) {
            //request weather data from phone app
            requestInitialData();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }

    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
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
