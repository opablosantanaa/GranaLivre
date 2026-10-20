package com.opablosantanaa.granalivre;

import android.app.Application;

import com.opablosantanaa.granalivre.worker.RecurrenceWorker;

public class GranaLivreApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RecurrenceWorker.schedule(this);
    }
}
