package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


public class SipDatePair implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String sip;
    protected java.util.Calendar dt;


    /**
     * Gets the value of the sip property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getSip() {
        return sip;
    }

    /**
     * Sets the value of the sip property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setSip(java.lang.String value) {
        this.sip = value;
    }
    /**
     * Gets the value of the dt property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getDt() {
        return dt;
    }

    /**
     * Sets the value of the dt property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setDt(java.util.Calendar value) {
        this.dt = value;
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
                return this.sip;
            case 1:
                return this.dt;
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
                info.name = "sip";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "dt";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
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
                this.sip = (java.lang.String)arg1;
                break;
            case 1:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.dt = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_dt = (String) arg1; 
                    if (str1_dt==null || str1_dt.length()==0){ 
                        this.dt = null; 
                    } else { 
                        try { 
                            this.dt = DateUtils.stringToCalendar(str1_dt);
                        } catch (Exception e) { 
                            Log.e("SipDatePair", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalDate().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalCalendar().register(soapEnvelope);
    } 

    @Override
    public String toString() {
        return "SipDatePair{"+"sip=" + this.sip+", dt=" + this.dt + '}';
    }
}
