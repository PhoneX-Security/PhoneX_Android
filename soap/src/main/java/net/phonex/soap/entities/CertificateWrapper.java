package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;

import org.kobjects.base64.Base64;


public class CertificateWrapper implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String user;
    protected byte[] certificate;
    protected net.phonex.soap.entities.CertificateStatus status;
    protected net.phonex.soap.entities.CertificateStatus providedCertStatus;


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
     * Gets the value of the certificate property.
     * 
     * @return
     *     possible object is
     *     {@link byte[] }
     *     
     */
    public byte[] getCertificate() {
        return certificate;
    }

    /**
     * Sets the value of the certificate property.
     * 
     * @param value
     *     allowed object is
     *     {@link byte[] }
     *     
     */
    public void setCertificate(byte[] value) {
        this.certificate = value;
    }
    /**
     * Gets the value of the status property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.CertificateStatus }
     *     
     */
    public net.phonex.soap.entities.CertificateStatus getStatus() {
        return status;
    }

    /**
     * Sets the value of the status property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.CertificateStatus }
     *     
     */
    public void setStatus(net.phonex.soap.entities.CertificateStatus value) {
        this.status = value;
    }
    /**
     * Gets the value of the providedCertStatus property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.CertificateStatus }
     *     
     */
    public net.phonex.soap.entities.CertificateStatus getProvidedCertStatus() {
        return providedCertStatus;
    }

    /**
     * Sets the value of the providedCertStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.CertificateStatus }
     *     
     */
    public void setProvidedCertStatus(net.phonex.soap.entities.CertificateStatus value) {
        this.providedCertStatus = value;
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
                return this.user;
            case 1:
                return this.certificate;
            case 2:
                return this.status == null ? null : this.status.toString().toLowerCase();
            case 3:
                return this.providedCertStatus == null ? null : this.providedCertStatus.toString().toLowerCase();
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
                info.name = "certificate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: com.phoenix.soap.beans.CertificateStatus
                info.name = "status";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: com.phoenix.soap.beans.CertificateStatus
                info.name = "providedCertStatus";
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
                    final String tmp_certificate = (String) arg1;
                    if (tmp_certificate==null || tmp_certificate.length()==0)
                        this.certificate = null; 
                    else
                        this.certificate = Base64.decode(tmp_certificate);
                } else if (arg1 instanceof byte[]){
                    this.certificate = (byte[]) arg1;
                } else { 
                    throw new IllegalArgumentException("Format unknown"); 
                }
                break;
            case 2:
                // type: com.phoenix.soap.beans.CertificateStatus
                this.status = net.phonex.soap.entities.CertificateStatus.fromValue((String) arg1);
                break;
            case 3:
                // type: com.phoenix.soap.beans.CertificateStatus
                this.providedCertStatus = net.phonex.soap.entities.CertificateStatus.fromValue((String) arg1);
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalBase64().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "providedCertStatus", net.phonex.soap.entities.CertificateStatus.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "status", net.phonex.soap.entities.CertificateStatus.class); // same package
    } 

    @Override
    public String toString() {
        return "CertificateWrapper{"+"user=" + this.user+", certificate=" + this.certificate+", status=" + this.status+", providedCertStatus=" + this.providedCertStatus + '}';
    }
}
