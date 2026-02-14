package com.minijarvis.app;

import android.app.Application;
import android.util.Log;

/**
 * Application class for MiniJarvis
 */
public class MiniJarvisApplication extends Application {
    private static final String TAG = "MiniJarvisApp";
    
    private static MiniJarvisApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "MiniJarvis application started");
    }
    
    public static MiniJarvisApplication getInstance() {
        return instance;
    }
}