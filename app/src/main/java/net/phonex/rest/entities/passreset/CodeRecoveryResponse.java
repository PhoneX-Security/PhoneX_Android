package net.phonex.rest.entities.passreset;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by miroc on 5.1.16.
 */
public class CodeRecoveryResponse {
    @SerializedName("validTo")
    @Expose
    private Long validTo;
    @SerializedName("statusCode")
    @Expose
    private Integer statusCode;
    @SerializedName("statusText")
    @Expose
    private String statusText;

    /**
     *
     * @return
     * The validTo
     */
    public Long getValidTo() {
        return validTo;
    }

    /**
     *
     * @param validTo
     * The validTo
     */
    public void setValidTo(Long validTo) {
        this.validTo = validTo;
    }

    /**
     *
     * @return
     * The statusCode
     */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     *
     * @param statusCode
     * The statusCode
     */
    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    /**
     *
     * @return
     * The statusText
     */
    public String getStatusText() {
        return statusText;
    }

    /**
     *
     * @param statusText
     * The statusText
     */
    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    @Override
    public String toString() {
        return "CodeRecoveryResponse{" +
                "validTo=" + validTo +
                ", statusCode=" + statusCode +
                ", statusText='" + statusText + '\'' +
                '}';
    }
}
