package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class FtGetStoredDHKeysInfoResponse implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected net.phonex.soap.entities.FtDHKeyUserInfoArr info;
    protected net.phonex.soap.entities.FtDHKeyUserStatsArr stats;


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
     * Gets the value of the info property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.FtDHKeyUserInfoArr }
     *     
     */
    public net.phonex.soap.entities.FtDHKeyUserInfoArr getInfo() {
        return info;
    }

    /**
     * Sets the value of the info property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.FtDHKeyUserInfoArr }
     *     
     */
    public void setInfo(net.phonex.soap.entities.FtDHKeyUserInfoArr value) {
        this.info = value;
    }
    /**
     * Gets the value of the stats property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.FtDHKeyUserStatsArr }
     *     
     */
    public net.phonex.soap.entities.FtDHKeyUserStatsArr getStats() {
        return stats;
    }

    /**
     * Sets the value of the stats property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.FtDHKeyUserStatsArr }
     *     
     */
    public void setStats(net.phonex.soap.entities.FtDHKeyUserStatsArr value) {
        this.stats = value;
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
        return 3;
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
                return this.info;
            case 2:
                return this.stats;
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
                // type: com.phoenix.soap.beans.FtDHKeyUserInfoArr
                info.name = "info";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.FtDHKeyUserInfoArr.class;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtDHKeyUserStatsArr
                info.name = "stats";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.FtDHKeyUserStatsArr.class;
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
                // type: com.phoenix.soap.beans.FtDHKeyUserInfoArr
                this.info = (net.phonex.soap.entities.FtDHKeyUserInfoArr)arg1;
                break;
            case 2:
                // type: com.phoenix.soap.beans.FtDHKeyUserStatsArr
                this.stats = (net.phonex.soap.entities.FtDHKeyUserStatsArr)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftGetStoredDHKeysInfoResponse", net.phonex.soap.entities.FtGetStoredDHKeysInfoResponse.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "stats", net.phonex.soap.entities.FtDHKeyUserStatsArr.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "info", net.phonex.soap.entities.FtDHKeyUserInfoArr.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "FtDHKeyUserInfoArr", net.phonex.soap.entities.FtDHKeyUserInfoArr.class); // package i class
        new net.phonex.soap.entities.FtDHKeyUserInfoArr().register(soapEnvelope); // registerable 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "FtDHKeyUserStatsArr", net.phonex.soap.entities.FtDHKeyUserStatsArr.class); // package i class
        new net.phonex.soap.entities.FtDHKeyUserStatsArr().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "FtGetStoredDHKeysInfoResponse{"+"errCode=" + this.errCode+", info=" + this.info+", stats=" + this.stats + '}';
    }
}
