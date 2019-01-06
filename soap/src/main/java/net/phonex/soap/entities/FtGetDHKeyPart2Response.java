package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;

import org.kobjects.base64.Base64;


public class FtGetDHKeyPart2Response implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected java.lang.String user;
    protected java.lang.String nonce2;
    protected byte[] sig2;


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
        return 4;
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
                return this.nonce2;
            case 3:
                return this.sig2;
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
                // type: java.lang.String
                info.name = "nonce2";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: byte[]
                info.name = "sig2";
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
                // type: int
                this.errCode = Integer.parseInt(arg1.toString());
                break;
            case 1:
                // type: java.lang.String
                this.user = (java.lang.String)arg1;
                break;
            case 2:
                // type: java.lang.String
                this.nonce2 = (java.lang.String)arg1;
                break;
            case 3:
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
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalBase64().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftGetDHKeyPart2Response", net.phonex.soap.entities.FtGetDHKeyPart2Response.class); // root
    } 

    @Override
    public String toString() {
        return "FtGetDHKeyPart2Response{"+"errCode=" + this.errCode+", user=" + this.user+", nonce2=" + this.nonce2+", sig2=" + this.sig2 + '}';
    }
}
