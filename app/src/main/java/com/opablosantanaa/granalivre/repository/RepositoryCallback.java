package com.opablosantanaa.granalivre.repository;

public interface RepositoryCallback<T> {
    void onSuccess(T result);

    void onError(Exception error);
}
