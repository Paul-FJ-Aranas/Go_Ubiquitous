package com.example.android.sunshine.app;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Paul Aranas on 5/9/2016.
 */
public class DataListenerService extends WearableListenerService {
    public final static String CONDITION_ID = "com.example.android.sunshine.app.id";
    public final static String MAX_TEMP = "com.example.android.sunshine.app.max";
    public final static String MIN_TEMP = "com.example.android.sunshine.app.min";
    private static final String WEARABLE_PATH = "/wearable_data";
//We are listening for weather data from mobile to populate the watch face
    //The data is stored in shared preferences and retrieved in the watch face
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        for (DataEvent event : dataEvents) {
            DataItem item = event.getDataItem();
            if (WEARABLE_PATH.equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                int mWeatherId = dataMap.getInt(CONDITION_ID);
                String mMaxTemp = dataMap.getString(MAX_TEMP);
                String mMinTemp = dataMap.getString(MIN_TEMP);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putInt("id", mWeatherId);
                edit.putString("min", mMinTemp);
                edit.putString("max", mMaxTemp);
                edit.commit();
            }


        }
    }
}

