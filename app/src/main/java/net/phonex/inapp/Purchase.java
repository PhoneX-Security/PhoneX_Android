/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.phonex.inapp;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import net.phonex.PhonexSettings;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app billing purchase.
 */
public class Purchase implements Parcelable {
    private static final String INAPP_ITEM_TYPE = "INAPP_ITEM_TYPE";
    private static final String INAPP_APP_VERSION = "INAPP_APP_VERSION";
    String mItemType;  // ITEM_TYPE_INAPP or ITEM_TYPE_SUBS
    String mOrderId;
    String mPackageName;
    String mSku;
    long mPurchaseTime;
    int mPurchaseState;
    String mDeveloperPayload;
    String mToken;
    String mOriginalJson;
    String mSignature;

    public Purchase(String itemType, String jsonPurchaseInfo, String signature) throws JSONException {
        mItemType = itemType;
        mOriginalJson = jsonPurchaseInfo;
        JSONObject o = new JSONObject(mOriginalJson);
        mOrderId = o.optString("orderId");
        mPackageName = o.optString("packageName");
        mSku = o.optString("productId");
        mPurchaseTime = o.optLong("purchaseTime");
        mPurchaseState = o.optInt("purchaseState");
        mDeveloperPayload = o.optString("developerPayload");
        mToken = o.optString("token", o.optString("purchaseToken"));
        mSignature = signature;
    }

    public String getItemType() { return mItemType; }
    public String getOrderId() { return mOrderId; }
    public String getPackageName() { return mPackageName; }
    public String getSku() { return mSku; }
    public long getPurchaseTime() { return mPurchaseTime; }
    public int getPurchaseState() { return mPurchaseState; }
    public String getDeveloperPayload() { return mDeveloperPayload; }
    public String getToken() { return mToken; }
    public String getOriginalJson() { return mOriginalJson; }
    public String getSignature() { return mSignature; }

    @Override
    public String toString() { return "PurchaseInfo(type:" + mItemType + "):" + mOriginalJson; }

    public JSONObject toJsonWithAppVersion(Context context) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(IabHelper.RESPONSE_INAPP_PURCHASE_DATA, mOriginalJson);
        jsonObject.put(IabHelper.RESPONSE_INAPP_SIGNATURE, mSignature);
        jsonObject.put(INAPP_ITEM_TYPE, mItemType);
        jsonObject.put(INAPP_APP_VERSION, PhonexSettings.getUniversalApplicationDesc(context));
        return jsonObject;
    }

    protected Purchase(Parcel in) {
        mItemType = in.readString();
        mOrderId = in.readString();
        mPackageName = in.readString();
        mSku = in.readString();
        mPurchaseTime = in.readLong();
        mPurchaseState = in.readInt();
        mDeveloperPayload = in.readString();
        mToken = in.readString();
        mOriginalJson = in.readString();
        mSignature = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mItemType);
        dest.writeString(mOrderId);
        dest.writeString(mPackageName);
        dest.writeString(mSku);
        dest.writeLong(mPurchaseTime);
        dest.writeInt(mPurchaseState);
        dest.writeString(mDeveloperPayload);
        dest.writeString(mToken);
        dest.writeString(mOriginalJson);
        dest.writeString(mSignature);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Purchase> CREATOR = new Parcelable.Creator<Purchase>() {
        @Override
        public Purchase createFromParcel(Parcel in) {
            return new Purchase(in);
        }

        @Override
        public Purchase[] newArray(int size) {
            return new Purchase[size];
        }
    };

    public boolean isConsumable(){
        return IabHelper.ITEM_TYPE_INAPP.equals(getItemType());
    }
}
