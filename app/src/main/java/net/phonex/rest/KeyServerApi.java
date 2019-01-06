package net.phonex.rest;

import net.phonex.rest.entities.auth.products.Products;
import net.phonex.rest.entities.passreset.CodeRecoveryResponse;
import net.phonex.rest.entities.passreset.CodeRecoveryVerificationResponse;

import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Query;

/**
 * Soap/key server REST API interface
 * Created by miroc on 5.1.16
 */
public interface KeyServerApi {

    @GET("/phoenix/rest/rest/recoveryCode")
    void sendRecoveryCode(
            @Query("userName") String sip,
            @Query("resource") String xmppResource,
            @Query("appVersion") String appVersion,
            @Query("auxJSON") String auxJson,
            Callback<CodeRecoveryResponse> cb);

    @GET("/phoenix/rest/rest/verifyRecoveryCode")
    void verifyRecoveryCode(
            @Query("userName") String sip,
            @Query("resource") String xmppResource,
            @Query("appVersion") String appVersion,
            @Query("recoveryCode") String recoveryCode,
            @Query("auxJSON") String auxJson,
            Callback<CodeRecoveryVerificationResponse> cb);
}
