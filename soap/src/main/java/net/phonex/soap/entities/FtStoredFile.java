package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;

import org.kobjects.base64.Base64;


public class FtStoredFile implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String sender;
    protected java.util.Calendar sentDate;
    protected java.lang.String nonce2;
    protected java.lang.String hashMeta;
    protected java.lang.String hashPack;
    protected long sizeMeta;
    protected long sizePack;
    protected byte[] key;
    protected java.lang.Integer protocolVersion;


    /**
     * Gets the value of the sender property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getSender() {
        return sender;
    }

    /**
     * Sets the value of the sender property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setSender(java.lang.String value) {
        this.sender = value;
    }
    /**
     * Gets the value of the sentDate property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getSentDate() {
        return sentDate;
    }

    /**
     * Sets the value of the sentDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setSentDate(java.util.Calendar value) {
        this.sentDate = value;
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
     * Gets the value of the hashMeta property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getHashMeta() {
        return hashMeta;
    }

    /**
     * Sets the value of the hashMeta property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setHashMeta(java.lang.String value) {
        this.hashMeta = value;
    }
    /**
     * Gets the value of the hashPack property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getHashPack() {
        return hashPack;
    }

    /**
     * Sets the value of the hashPack property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setHashPack(java.lang.String value) {
        this.hashPack = value;
    }
    /**
     * Gets the value of the sizeMeta property.
     * 
     * @return
     *     possible object is
     *     {@link long }
     *     
     */
    public long getSizeMeta() {
        return sizeMeta;
    }

    /**
     * Sets the value of the sizeMeta property.
     * 
     * @param value
     *     allowed object is
     *     {@link long }
     *     
     */
    public void setSizeMeta(long value) {
        this.sizeMeta = value;
    }
    /**
     * Gets the value of the sizePack property.
     * 
     * @return
     *     possible object is
     *     {@link long }
     *     
     */
    public long getSizePack() {
        return sizePack;
    }

    /**
     * Sets the value of the sizePack property.
     * 
     * @param value
     *     allowed object is
     *     {@link long }
     *     
     */
    public void setSizePack(long value) {
        this.sizePack = value;
    }
    /**
     * Gets the value of the key property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Sets the value of the key property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setKey(byte[] value) {
        this.key = value;
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
        return 9;
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
                return this.sender;
            case 1:
                return this.sentDate;
            case 2:
                return this.nonce2;
            case 3:
                return this.hashMeta;
            case 4:
                return this.hashPack;
            case 5:
                return this.sizeMeta;
            case 6:
                return this.sizePack;
            case 7:
                return this.key;
            case 8:
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
                // type: java.lang.String
                info.name = "sender";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "sentDate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 2:
                // type: java.lang.String
                info.name = "nonce2";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: java.lang.String
                info.name = "hashMeta";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "hashPack";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: long
                info.name = "sizeMeta";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 6:
                // type: long
                info.name = "sizePack";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 7:
                // type: byte[]
                info.name = "key";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 8:
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
                // type: java.lang.String
                this.sender = (java.lang.String)arg1;
                break;
            case 1:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.sentDate = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_sentDate = (String) arg1; 
                    if (str1_sentDate==null || str1_sentDate.length()==0){ 
                        this.sentDate = null; 
                    } else { 
                        try { 
                            this.sentDate = DateUtils.stringToCalendar(str1_sentDate);
                        } catch (Exception e) { 
                            Log.e("FtStoredFile", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 2:
                // type: java.lang.String
                this.nonce2 = (java.lang.String)arg1;
                break;
            case 3:
                // type: java.lang.String
                this.hashMeta = (java.lang.String)arg1;
                break;
            case 4:
                // type: java.lang.String
                this.hashPack = (java.lang.String)arg1;
                break;
            case 5:
                // type: long
                this.sizeMeta = Long.parseLong(arg1.toString());
                break;
            case 6:
                // type: long
                this.sizePack = Long.parseLong(arg1.toString());
                break;
            case 7:
                // type: byte[]
                if (arg1 instanceof String){ 
                    final String tmp_key = (String) arg1;
                    if (tmp_key==null || tmp_key.length()==0)
                        this.key = null; 
                    else
                        this.key = Base64.decode(tmp_key);
                } else if (arg1 instanceof byte[]){
                    this.key = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
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
                       Log.ef("FtStoredFile", e, "Problem with protocolVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("FtStoredFile", "Problem with protocolVersion parsing - unknown type");
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
    } 

    @Override
    public String toString() {
        return "FtStoredFile{"+"sender=" + this.sender+", sentDate=" + this.sentDate+", nonce2=" + this.nonce2+", hashMeta=" + this.hashMeta+", hashPack=" + this.hashPack+", sizeMeta=" + this.sizeMeta+", sizePack=" + this.sizePack+", key=" + this.key+", protocolVersion=" + this.protocolVersion + '}';
    }
}
