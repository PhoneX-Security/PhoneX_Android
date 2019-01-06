package net.phonex.soap.entities;

import net.phonex.ksoap2.base64.Base64;
import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


public class FtGetDHKeyResponse implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected java.lang.String user;
    protected byte[] aEncBlock;
    protected byte[] sEncBlock;
    protected byte[] sig1;
    protected java.util.Calendar created;
    protected java.util.Calendar expires;
    protected java.lang.Integer protocolVersion;


    /**
     * Gets the value of the errCode property.
     * 
     * @return
     *     possible object is
     *     {@link int }
     *     
     */
    public int getErrCode() {
        return errCode;
    }

    /**
     * Sets the value of the errCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link int }
     *     
     */
    public void setErrCode(int value) {
        this.errCode = value;
    }
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
     * Gets the value of the created property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getCreated() {
        return created;
    }

    /**
     * Sets the value of the created property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setCreated(java.util.Calendar value) {
        this.created = value;
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
        return 8;
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
                return this.errCode;
            case 1:
                return this.user;
            case 2:
                return this.aEncBlock;
            case 3:
                return this.sEncBlock;
            case 4:
                return this.sig1;
            case 5:
                return this.created;
            case 6:
                return this.expires;
            case 7:
                return this.protocolVersion;
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
                // type: int
                info.name = "errCode";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 1:
                // type: java.lang.String
                info.name = "user";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: byte[]
                info.name = "aEncBlock";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: byte[]
                info.name = "sEncBlock";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: byte[]
                info.name = "sig1";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "created";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 6:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "expires";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 7:
                // type: java.lang.Integer
                info.name = "protocolVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
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
                // type: int
                this.errCode = Integer.parseInt(arg1.toString());
                break;
            case 1:
                // type: java.lang.String
                this.user = (java.lang.String)arg1;
                break;
            case 2:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_aEncBlock = (String) arg1;
                    if (tmp_aEncBlock==null || tmp_aEncBlock.length()==0)
                        this.aEncBlock = null; 
                    else
                        this.aEncBlock = Base64.decode(tmp_aEncBlock);
//                                org.spongycastle.util.encoders.Base64.decode();
                } else if (arg1 instanceof byte[]){
                    this.aEncBlock = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 3:
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
            case 4:
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
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.created = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_created = (String) arg1; 
                    if (str1_created==null || str1_created.length()==0){ 
                        this.created = null; 
                    } else { 
                        try { 
                            this.created = DateUtils.stringToCalendar(str1_created);
                        } catch (Exception e) { 
                            Log.e("FtGetDHKeyResponse", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 6:
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
                            Log.e("FtGetDHKeyResponse", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 7:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.protocolVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.protocolVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("FtGetDHKeyResponse", e, "Problem with protocolVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtGetDHKeyResponse", "Problem with protocolVersion parsing - unknown type");
               }
              
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
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftGetDHKeyResponse", net.phonex.soap.entities.FtGetDHKeyResponse.class); // root
    } 

    @Override
    public String toString() {
        return "FtGetDHKeyResponse{"+"errCode=" + this.errCode+", user=" + this.user+", aEncBlock=" + this.aEncBlock+", sEncBlock=" + this.sEncBlock+", sig1=" + this.sig1+", created=" + this.created+", expires=" + this.expires+", protocolVersion=" + this.protocolVersion + '}';
    }
}
