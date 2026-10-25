package com.opablosantanaa.granalivre;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;

import com.opablosantanaa.granalivre.ui.Session;
import com.opablosantanaa.granalivre.worker.BackupWorker;
import com.opablosantanaa.granalivre.worker.DueReminderWorker;
import com.opablosantanaa.granalivre.worker.RecurrenceWorker;

public class GranaLivreApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build());
        }
        Session.applyTheme(Session.theme(this));
        RecurrenceWorker.schedule(this);
        DueReminderWorker.schedule(this);
        BackupWorker.schedule(this);
    }
}
