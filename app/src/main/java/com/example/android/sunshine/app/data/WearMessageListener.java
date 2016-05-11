package com.example.android.sunshine.app.data;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Paul Aranas on 5/9/2016.
 */
//gets a message from the wearable when it is first initialized so that the watch will have
    // data when it is first activated
public class WearMessageListener extends WearableListenerService {
    private static final String TAG = "WearableListenerService";

    private static final String REQUEST_WEATHER_PATH = "/weather_initial_request";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d("TAG11", "onMESSssageReceived: ");

        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            Log.d("TAG11", "message path");

            if (item.equals(REQUEST_WEATHER_PATH)) {
                // start service sending updated weather condition to wear
                Context context = this.getApplicationContext();
                context.startService(new Intent(context, WearIntentService.class));
            }


        }
    }
}


