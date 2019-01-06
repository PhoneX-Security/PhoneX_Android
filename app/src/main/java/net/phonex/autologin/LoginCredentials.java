package net.phonex.autologin;

import net.phonex.core.SipUri;

/**
 * Created by Matus on 04-Sep-15.
 */
public class LoginCredentials {
    public final String password;
    public final String userName;
    public final String domain;

    public static LoginCredentials from(String password, String sip){
        SipUri.ParsedSipContactInfos parseSipContact = SipUri.parseSipContact(sip);
        return  new LoginCredentials(password, parseSipContact.userName, parseSipContact.domain);
    }

    public LoginCredentials(String password, String username, String domain) {
        this.password = password;
        this.userName = username;
        this.domain = domain;
    }

    public String getSip(){
        return userName + "@" + domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LoginCredentials that = (LoginCredentials) o;

        if (password != null ? !password.equals(that.password) : that.password != null)
            return false;
        if (userName != null ? !userName.equals(that.userName) : that.userName != null)
            return false;
        return domain != null ? domain.equals(that.domain) : that.domain == null;

    }

    @Override
    public int hashCode() {
        int result = password != null ? password.hashCode() : 0;
        result = 31 * result + (userName != null ? userName.hashCode() : 0);
        result = 31 * result + (domain != null ? domain.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LoginCredentials{" +
                "password='" + password + '\'' +
                ", userName='" + userName + '\'' +
                ", domain='" + domain + '\'' +
                '}';
    }
}
