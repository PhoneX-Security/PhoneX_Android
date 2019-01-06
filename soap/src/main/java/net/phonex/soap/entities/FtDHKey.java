package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;

import org.kobjects.base64.Base64;


public class FtDHKey implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String user;
    protected byte[] aEncBlock;
    protected byte[] sEncBlock;
    protected java.lang.String nonce1;
    protected java.lang.String nonce2;
    protected byte[] sig1;
    protected byte[] sig2;
    protected java.util.Calendar expires;
    protected java.lang.Integer protocolVersion;
    protected java.lang.Integer version;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;
    protected java.lang.String creatorCertInfo;
    protected java.lang.String userCertInfo;


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
     * Gets the value of the aEncBlock property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getAEncBlock() {
        return aEncBlock;
    }

    /**
     * Sets the value of the aEncBlock property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setAEncBlock(byte[] value) {
        this.aEncBlock = value;
    }
    /**
     * Gets the value of the sEncBlock property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getSEncBlock() {
        return sEncBlock;
    }

    /**
     * Sets the value of the sEncBlock property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setSEncBlock(byte[] value) {
        this.sEncBlock = value;
    }
    /**
     * Gets the value of the nonce1 property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getNonce1() {
        return nonce1;
    }

    /**
     * Sets the value of the nonce1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setNonce1(java.lang.String value) {
        this.nonce1 = value;
    }
    /**
     * Gets the value of the nonce2 property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getNonce2() {
        return nonce2;
    }

    /**
     * Sets the value of the nonce2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setNonce2(java.lang.String value) {
        this.nonce2 = value;
    }
    /**
     * Gets the value of the sig1 property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getSig1() {
        return sig1;
    }

    /**
     * Sets the value of the sig1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setSig1(byte[] value) {
        this.sig1 = value;
    }
    /**
     * Gets the value of the sig2 property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getSig2() {
        return sig2;
    }

    /**
     * Sets the value of the sig2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setSig2(byte[] value) {
        this.sig2 = value;
    }
    /**
     * Gets the value of the expires property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getExpires() {
        return expires;
    }

    /**
     * Sets the value of the expires property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setExpires(java.util.Calendar value) {
        this.expires = value;
    }
    /**
     * Gets the value of the protocolVersion property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Sets the value of the protocolVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setProtocolVersion(java.lang.Integer value) {
        this.protocolVersion = value;
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
     * Gets the value of the creatorCertInfo property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getCreatorCertInfo() {
        return creatorCertInfo;
    }

    /**
     * Sets the value of the creatorCertInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setCreatorCertInfo(java.lang.String value) {
        this.creatorCertInfo = value;
    }
    /**
     * Gets the value of the userCertInfo property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getUserCertInfo() {
        return userCertInfo;
    }

    /**
     * Sets the value of the userCertInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setUserCertInfo(java.lang.String value) {
        this.userCertInfo = value;
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
        return 14;
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
                return this.aEncBlock;
            case 2:
                return this.sEncBlock;
            case 3:
                return this.nonce1;
            case 4:
                return this.nonce2;
            case 5:
                return this.sig1;
            case 6:
                return this.sig2;
            case 7:
                return this.expires;
            case 8:
                return this.protocolVersion;
            case 9:
                return this.version;
            case 10:
                return this.auxVersion;
            case 11:
                return this.auxJSON;
            case 12:
                return this.creatorCertInfo;
            case 13:
                return this.userCertInfo;
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
                // type: byte[]
                info.name = "aEncBlock";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: byte[]
                info.name = "sEncBlock";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "nonce1";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "nonce2";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: byte[]
                info.name = "sig1";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 6:
                // type: byte[]
                info.name = "sig2";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "expires";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 8:
                // type: java.lang.Integer
                info.name = "protocolVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 9:
                // type: java.lang.Integer
                info.name = "version";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 10:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 11:
                // type: java.lang.String
                info.name = "auxJSON";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 12:
                // type: java.lang.String
                info.name = "creatorCertInfo";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 13:
                // type: java.lang.String
                info.name = "userCertInfo";
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
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_aEncBlock = (String) arg1;
                    if (tmp_aEncBlock==null || tmp_aEncBlock.length()==0)
                        this.aEncBlock = null; 
                    else
                        this.aEncBlock = Base64.decode(tmp_aEncBlock);
                } else if (arg1 instanceof byte[]){
                    this.aEncBlock = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 2:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_sEncBlock = (String) arg1;
                    if (tmp_sEncBlock==null || tmp_sEncBlock.length()==0)
                        this.sEncBlock = null; 
                    else
                        this.sEncBlock = Base64.decode(tmp_sEncBlock);
                } else if (arg1 instanceof byte[]){
                    this.sEncBlock = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 3:
                // type: java.lang.String
                this.nonce1 = (java.lang.String)arg1;
                break;
            case 4:
                // type: java.lang.String
                this.nonce2 = (java.lang.String)arg1;
                break;
            case 5:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_sig1 = (String) arg1;
                    if (tmp_sig1==null || tmp_sig1.length()==0)
                        this.sig1 = null; 
                    else
                        this.sig1 = Base64.decode(tmp_sig1);
                } else if (arg1 instanceof byte[]){
                    this.sig1 = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 6:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_sig2 = (String) arg1;
                    if (tmp_sig2==null || tmp_sig2.length()==0)
                        this.sig2 = null; 
                    else
                        this.sig2 = Base64.decode(tmp_sig2);
                } else if (arg1 instanceof byte[]){
                    this.sig2 = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 7:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.expires = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_expires = (String) arg1; 
                    if (str1_expires==null || str1_expires.length()==0){ 
                        this.expires = null; 
                    } else { 
                        try { 
                            this.expires = DateUtils.stringToCalendar(str1_expires);
                        } catch (Exception e) { 
                            Log.e("FtDHKey", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 8:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.protocolVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.protocolVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtDHKey", e, "Problem with protocolVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtDHKey", "Problem with protocolVersion parsing - unknown type");
               }
              
                break;
            case 9:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.version = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.version = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtDHKey", e, "Problem with version parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtDHKey", "Problem with version parsing - unknown type");
               }
              
                break;
            case 10:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtDHKey", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtDHKey", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 11:
                // type: java.lang.String
                this.auxJSON = (java.lang.String)arg1;
                break;
            case 12:
                // type: java.lang.String
                this.creatorCertInfo = (java.lang.String)arg1;
                break;
            case 13:
                // type: java.lang.String
                this.userCertInfo = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalBase64().register(soapEnvelope);
        new net.phonex.ksoap2.serialization.MarshalDate().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalCalendar().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalInteger().register(soapEnvelope);
    } 

    @Override
    public String toString() {
        return "FtDHKey{"+"user=" + this.user+", aEncBlock=" + this.aEncBlock+", sEncBlock=" + this.sEncBlock+", nonce1=" + this.nonce1+", nonce2=" + this.nonce2+", sig1=" + this.sig1+", sig2=" + this.sig2+", expires=" + this.expires+", protocolVersion=" + this.protocolVersion+", version=" + this.version+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON+", creatorCertInfo=" + this.creatorCertInfo+", userCertInfo=" + this.userCertInfo + '}';
    }
}
