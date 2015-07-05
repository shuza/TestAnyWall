package com.shuza.testanywall;

import android.content.SharedPreferences;

import com.parse.Parse;

/**
 * Created by Boka on 27-Jun-15.
 */
public class Application extends android.app.Application {

    public static final String INTENT_EXTRA_LOCATION = "location";
    private static final String KEY_SEARCH_DISTANCE = "searchDistance";
    private static final float DEFAULT_SEARCH_DISTANCE = 250.0f;
    private static SharedPreferences preferences;

    public static final String APPTAG = "AnyWall";

    @Override
    public void onCreate() {
        super.onCreate();
        Parse.enableLocalDatastore(this);

        Parse.initialize(this, "lr5wbjmIa3pbmviHDg0NcG2pRHnU8PN8PmPJ6gII", "qPe6WVbgHxEqe2D1YDdiYYA5K2L7r1g3nBudkXmG");
    }
}
