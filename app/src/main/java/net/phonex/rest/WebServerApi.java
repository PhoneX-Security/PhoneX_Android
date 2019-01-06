package net.phonex.rest;


import net.phonex.rest.entities.Version;

import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Query;

/**
 * Created by miroc on 12.5.15.
 */
public interface WebServerApi {
    @GET("/api/newest-version?type=android")
    void getNewestVersion(Callback<Version> cb);

    @GET("/api/version?type=android")
    void getVersion(@Query("versionCode") int versionCode, Callback<Version> cb);
}
