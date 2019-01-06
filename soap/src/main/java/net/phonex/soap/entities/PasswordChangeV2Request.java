package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.Log;

import org.kobjects.base64.Base64;


public class PasswordChangeV2Request implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String user;
    protected java.lang.String targetUser;
    protected java.lang.String usrToken;
    protected java.lang.String serverToken;
    protected java.lang.String authHash;
    protected byte[] newHA1;
    protected byte[] newHA1B;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


    /**
     * Gets the value of the user property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUser() {
        return user;
    }

    /**
     * Sets the value of the user property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUser(java.lang.String value) {
        this.user = value;
    }
    /**
     * Gets the value of the targetUser property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getTargetUser() {
        return targetUser;
    }

    /**
     * Sets the value of the targetUser property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setTargetUser(java.lang.String value) {
        this.targetUser = value;
    }
    /**
     * Gets the value of the usrToken property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUsrToken() {
        return usrToken;
    }

    /**
     * Sets the value of the usrToken property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUsrToken(java.lang.String value) {
        this.usrToken = value;
    }
    /**
     * Gets the value of the serverToken property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getServerToken() {
        return serverToken;
    }

    /**
     * Sets the value of the serverToken property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setServerToken(java.lang.String value) {
        this.serverToken = value;
    }
    /**
     * Gets the value of the authHash property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getAuthHash() {
        return authHash;
    }

    /**
     * Sets the value of the authHash property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setAuthHash(java.lang.String value) {
        this.authHash = value;
    }
    /**
     * Gets the value of the newHA1 property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getNewHA1() {
        return newHA1;
    }

    /**
     * Sets the value of the newHA1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setNewHA1(byte[] value) {
        this.newHA1 = value;
    }
    /**
     * Gets the value of the newHA1B property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getNewHA1B() {
        return newHA1B;
    }

    /**
     * Sets the value of the newHA1B property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setNewHA1B(byte[] value) {
        this.newHA1B = value;
    }
    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setVersion(java.lang.Integer value) {
        this.version = value;
    }
    /**
     * Gets the value of the auxVersion property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getAuxVersion() {
        return auxVersion;
    }

    /**
     * Sets the value of the auxVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setAuxVersion(java.lang.Integer value) {
        this.auxVersion = value;
    }
    /**
     * Gets the value of the auxJSON property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getAuxJSON() {
        return auxJSON;
    }

    /**
     * Sets the value of the auxJSON property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setAuxJSON(java.lang.String value) {
        this.auxJSON = value;
    }

    /**
     * Gets the value of the ignoreNullWrappers property.
     * 
     * @return
     *     possible object is
     *     {@link boolean }
     *     
     */
    public boolean getIgnoreNullWrappers() {
        return ignoreNullWrappers;
    }

    /**
     * Sets the value of the ignoreNullWrappers property.
     * 
     * @param value
     *     allowed object is
     *     {@link boolean }
     *     
     */
    public void setIgnoreNullWrappers(boolean value) {
        this.ignoreNullWrappers = value;
    }

    @Override 
    public int getPropertyCount() { 
        return 10;
    } 


    /**
     * Computes index shift for serialization methods in order to ignore null
     * wrappers during serialization so as not to produce lists with one empty
     * instance
     */
     protected int ignoreNullWrapperShift(int idx) {
        return idx;
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#getProperty(int)
      */
    @Override
    public Object getProperty(int index) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                return this.user;
            case 1:
                return this.targetUser;
            case 2:
                return this.usrToken;
            case 3:
                return this.serverToken;
            case 4:
                return this.authHash;
            case 5:
                return this.newHA1;
            case 6:
                return this.newHA1B;
            case 7:
                return this.version;
            case 8:
                return this.auxVersion;
            case 9:
                return this.auxJSON;
            default:
                return null;
        }
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#getPropertyInfo(int,
      * java.util.Hashtable, net.phonex.ksoap2.serialization.PropertyInfo)
      */
    @Override
    public void getPropertyInfo(int index, Hashtable arg1, PropertyInfo info) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                // type: java.lang.String
                info.name = "user";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: java.lang.String
                info.name = "targetUser";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: java.lang.String
                info.name = "usrToken";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "serverToken";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "authHash";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: byte[]
                info.name = "newHA1";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 6:
                // type: byte[]
                info.name = "newHA1B";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 8:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 9:
                // type: java.lang.String
                info.name = "auxJSON";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            default:
                break;
        }
    }

     /*
      * (non-Javadoc)
      * 
      * @see net.phonex.ksoap2.serialization.KvmSerializable#setProperty(int,
      * java.lang.Object)
      */
     @Override
     public void setProperty(int index, Object arg1) {
        index = this.ignoreNullWrapperShift(index);
        switch (index){
            case 0:
                // type: java.lang.String
                this.user = (java.lang.String)arg1;
                break;
            case 1:
                // type: java.lang.String
                this.targetUser = (java.lang.String)arg1;
                break;
            case 2:
                // type: java.lang.String
                this.usrToken = (java.lang.String)arg1;
                break;
            case 3:
                // type: java.lang.String
                this.serverToken = (java.lang.String)arg1;
                break;
            case 4:
                // type: java.lang.String
                this.authHash = (java.lang.String)arg1;
                break;
            case 5:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_newHA1 = (String) arg1;
                    if (tmp_newHA1==null || tmp_newHA1.length()==0)
                        this.newHA1 = null; 
                    else
                        this.newHA1 = Base64.decode(tmp_newHA1);
                } else if (arg1 instanceof byte[]){
                    this.newHA1 = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 6:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_newHA1B = (String) arg1;
                    if (tmp_newHA1B==null || tmp_newHA1B.length()==0)
                        this.newHA1B = null; 
                    else
                        this.newHA1B = Base64.decode(tmp_newHA1B);
                } else if (arg1 instanceof byte[]){
                    this.newHA1B = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 7:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("PasswordChangeV2Request", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("PasswordChangeV2Request", "Problem with version parsing - unknown type");
               }
              
                break;
            case 8:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("PasswordChangeV2Request", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("PasswordChangeV2Request", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 9:
                // type: java.lang.String
                this.auxJSON = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalBase64().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalInteger().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "passwordChangeV2Request", net.phonex.soap.entities.PasswordChangeV2Request.class); // root
    } 

    @Override
    public String toString() {
        return "PasswordChangeV2Request{"+"user=" + this.user+", targetUser=" + this.targetUser+", usrToken=" + this.usrToken+", serverToken=" + this.serverToken+", authHash=" + this.authHash+", newHA1=" + this.newHA1+", newHA1B=" + this.newHA1B+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}
