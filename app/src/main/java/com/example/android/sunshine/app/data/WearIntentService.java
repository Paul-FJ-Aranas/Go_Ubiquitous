package com.example.android.sunshine.app.data;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Paul Aranas on 5/9/2016.
 * Service to send initial weather data when watch is first loaded
 */
    public class WearIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks {

        /**
         * Creates an IntentService.  Invoked by your subclass's constructor.
         *
         * @param name Used to name the worker thread, important only for debugging.
         */
        //Whe we receive the message that the watch has been paired with the mobile
                // this intent service is launched and weather data is sent back to watch
        GoogleApiClient mGoogleApiClient;
        public final static String CONDITION_ID = "com.example.android.sunshine.app.id";
        public final static String MAX_TEMP = "com.example.android.sunshine.app.max";
        public final static String MIN_TEMP = "com.example.android.sunshine.app.min";
        public final static String LOCATION = "com.example.android.sunshine.app.loc";
        private static final String REQUEST_PATH = "/phone2wear";
        private static final int INDEX_WEATHER_ID = 0;
        private static final int INDEX_MIN = 1;
        private static final int INDEX_MAX = 2;
        private int mId;
        private double mMax;
        private double mMin;
        private String mLoc;


        private static final String[] WEATHER_FORECAST_COLUMNS = {
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP

        };


        public WearIntentService(String name) {
            super(name);
        }


    @Override
        protected void onHandleIntent(Intent intent) {

            String location = Utility.getPreferredLocation(this);
            Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                    location, System.currentTimeMillis());
            Cursor data = getContentResolver().query(weatherForLocationUri, WEATHER_FORECAST_COLUMNS, null,
                    null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");

            if (!data.moveToFirst()) {
                data.close();
                return;
            }
            if (data == null) {
                return;
            }

            mId = data.getInt(INDEX_WEATHER_ID);
            mMax = data.getDouble(INDEX_MAX);
            mMin = data.getDouble(INDEX_MIN);
            mLoc = Utility.getPreferredLocation(this).trim().toUpperCase();
            data.close();

            if (null == mGoogleApiClient) {

                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(Wearable.API)
                        .addConnectionCallbacks(this)
                        .build();
            }
            if (!mGoogleApiClient.isConnected()) {
                Log.d("TAG11", "BEING CALLED REQUESTTTTTTTTTTTTTT");
                mGoogleApiClient.connect();
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(REQUEST_PATH);
            putDataMapRequest.setUrgent();
            //Put Id, Max Temp, Min Temp, and Location into DataMap
            putDataMapRequest.getDataMap().putInt(CONDITION_ID, mId);
            putDataMapRequest.getDataMap().putDouble(MAX_TEMP, mMax);
            putDataMapRequest.getDataMap().putDouble(MIN_TEMP, mMin);
            putDataMapRequest.getDataMap().putString(LOCATION, mLoc);

            // timestamp to create unique updates
            putDataMapRequest.getDataMap().putLong("current_time", System.currentTimeMillis());

            PutDataRequest request = putDataMapRequest.asPutDataRequest();
            request.setUrgent();
            Log.d("TAG11", "BEING CALLED REQUESTTTTTTTTTTTTTT");
            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                                           @Override
                                           public void onResult(DataApi.DataItemResult dataItemResult) {
                                               if (dataItemResult.getStatus().isSuccess()) {

                                               } else {

                                               }
                                           }
                                       }
                    );
        }


    @Override
        public void onConnectionSuspended(int i) {

        }
    }

