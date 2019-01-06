package net.phonex.rest.entities.auth.purchase;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import net.phonex.rest.entities.auth.products.Product;

import java.util.ArrayList;
import java.util.List;

//@Generated("org.jsonschema2pojo")
public class Response{

    @SerializedName("responseCode")
    @Expose
    private Integer responseCode;

    @SerializedName("purchasesResponseCodes")
    @Expose
    private List<Integer> purchasesResponseCodes = new ArrayList<>();

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public List<Integer> getPurchasesResponseCodes() {
        return purchasesResponseCodes;
    }

    public void setPurchasesResponseCodes(List<Integer> purchasesResponseCodes) {
        this.purchasesResponseCodes = purchasesResponseCodes;
    }

    @Override
    public String toString() {
        return "Response{" +
                "responseCode=" + responseCode +
                ", purchasesResponseCodes=" + purchasesResponseCodes +
                '}';
    }
}