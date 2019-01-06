package net.phonex.rest;

import net.phonex.rest.entities.auth.products.Product;
import net.phonex.rest.entities.auth.products.Products;
import net.phonex.rest.entities.auth.purchase.Response;

import java.util.Map;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;

/**
 * License server authenticated API
 * Created by miroc on 12.5.15.
 */
public interface LicenseServerAuthApi {
    @GET("/api/auth/products/available")
    void availableProducts(@Query("locales") String concatenatedLocales, @Query("platform") String platform, Callback<Products> cb);

    @GET("/api/auth/products/available")
    void availableProducts(@Query("platform") String platform, Callback<Products> cb);

    @GET("/api/auth/products/list-from-licenses")
    void listProductsFromLicenseIds(@Query("ids") String concatenatedIds, Callback<Map<Integer, Product>> cb);

    @GET("/api/auth/products/list-from-licenses")
    void listProductsFromLicenseIds(@Query("locales") String concatenatedLocale, @Query("ids") String concatenatedIds, Callback<Map<Integer, Product>> cb);

    @FormUrlEncoded
    @POST("/api/auth/purchase/play/payment-verif")
    Response verifyPayment(@Field("request") String requestJson);

    @FormUrlEncoded
    @POST("/api/auth/purchase/play/payments-verif")
    Response verifyPayments(@Field("request") String requestJson);
}
