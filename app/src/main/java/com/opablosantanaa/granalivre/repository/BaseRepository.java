package com.opablosantanaa.granalivre.repository;

import android.os.Handler;
import android.os.Looper;

import com.opablosantanaa.granalivre.database.AppDatabase;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

public abstract class BaseRepository {
    protected final AppDatabase database;
    private final Executor backgroundExecutor;
    private final Executor callbackExecutor;

    protected BaseRepository(AppDatabase database, Executor backgroundExecutor, Executor callbackExecutor) {
        this.database = database;
        this.backgroundExecutor = backgroundExecutor;
        this.callbackExecutor = callbackExecutor;
    }

    protected static Executor mainThreadExecutor() {
        Handler handler = new Handler(Looper.getMainLooper());
        return handler::post;
    }

    protected <T> void execute(Callable<T> task, RepositoryCallback<T> callback) {
        backgroundExecutor.execute(() -> {
            try {
                T result = task.call();
                if (callback != null) {
                    callbackExecutor.execute(() -> callback.onSuccess(result));
                }
            } catch (Exception e) {
                if (callback != null) {
                    callbackExecutor.execute(() -> callback.onError(e));
                }
            }
        });
    }
}
