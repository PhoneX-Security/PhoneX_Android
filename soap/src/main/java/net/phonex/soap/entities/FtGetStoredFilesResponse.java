package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class FtGetStoredFilesResponse implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected net.phonex.soap.entities.FtStoredFileList storedFile;


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
     * Gets the value of the storedFile property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.FtStoredFileList }
     *     
     */
    public net.phonex.soap.entities.FtStoredFileList getStoredFile() {
        return storedFile;
    }

    /**
     * Sets the value of the storedFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.FtStoredFileList }
     *     
     */
    public void setStoredFile(net.phonex.soap.entities.FtStoredFileList value) {
        this.storedFile = value;
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
        return 2;
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
                return this.storedFile;
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
                // type: com.phoenix.soap.beans.FtStoredFileList
                info.name = "storedFile";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.FtStoredFileList.class;
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
                // type: com.phoenix.soap.beans.FtStoredFileList
                this.storedFile = (net.phonex.soap.entities.FtStoredFileList)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftGetStoredFilesResponse", net.phonex.soap.entities.FtGetStoredFilesResponse.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "storedFile", net.phonex.soap.entities.FtStoredFileList.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "FtStoredFileList", net.phonex.soap.entities.FtStoredFileList.class); // package i class
        new net.phonex.soap.entities.FtStoredFileList().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "FtGetStoredFilesResponse{"+"errCode=" + this.errCode+", storedFile=" + this.storedFile + '}';
    }
}
