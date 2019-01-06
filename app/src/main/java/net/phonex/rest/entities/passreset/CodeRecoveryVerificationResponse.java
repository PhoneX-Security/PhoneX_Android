package net.phonex.rest.entities.passreset;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by miroc on 5.1.16.
 */
public class CodeRecoveryVerificationResponse {
    @SerializedName("statusCode")
    @Expose
    private Integer statusCode;
    @SerializedName("statusText")
    @Expose
    private String statusText;
    @SerializedName("newPassword")
    @Expose
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
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
        return "CodeRecoveryVerificationResponse{" +
                "statusCode=" + statusCode +
                ", statusText='" + statusText + '\'' +
                ", newPassword='" + newPassword + '\'' +
                '}';
    }
}
