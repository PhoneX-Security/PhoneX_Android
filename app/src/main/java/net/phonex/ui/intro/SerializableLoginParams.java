package net.phonex.ui.intro;

import net.phonex.core.SipUri;

import java.io.Serializable;

/**
* Created by miroc on 10.2.15.
*/
public class SerializableLoginParams implements Serializable {
    private static final long serialVersionUID = 0L;

    public static SerializableLoginParams from(String sip, CharSequence password){
        SipUri.ParsedSipContactInfos parsedSip = SipUri.parseSipContact(sip);
        return new SerializableLoginParams(sip, parsedSip.userName, password, parsedSip.domain);
    }

    public SerializableLoginParams(String sip, String username, CharSequence password, String domain) {
        this.username = username;
        this.sip = sip;
        this.password = password;
        this.domain = domain;
    }
    public String username;
    public String sip;
    public CharSequence password;
    public String domain;
    public String expiryDate;

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    @Override
    public String toString() {
        return "LoginParams{" +
                "username='" + username + '\'' +
                ", sip='" + sip + '\'' +
                ", password=" + password +
                ", domain='" + domain + '\'' +
                '}';
    }
}
