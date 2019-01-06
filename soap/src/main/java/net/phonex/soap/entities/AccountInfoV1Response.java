package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


public class AccountInfoV1Response implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected int errCode;
    protected net.phonex.soap.entities.TrueFalse forcePasswordChange;
    protected java.lang.Integer storedFilesNum;
    protected java.util.Calendar serverTime;
    protected java.lang.String licenseType;
    protected java.util.Calendar accountIssued;
    protected java.util.Calendar accountExpires;
    protected java.util.Calendar firstAuthCheckDate;
    protected java.util.Calendar lastAuthCheckDate;
    protected java.util.Calendar firstLoginDate;
    protected java.util.Calendar firstUserAddDate;
    protected java.util.Calendar accountLastActivity;
    protected java.util.Calendar accountLastPassChange;
    protected java.lang.Boolean accountDisabled;
    protected java.lang.Integer auxVersion;
    protected java.lang.String auxJSON;


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
     * Gets the value of the forcePasswordChange property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.TrueFalse }
     *     
     */
    public net.phonex.soap.entities.TrueFalse getForcePasswordChange() {
        return forcePasswordChange;
    }

    /**
     * Sets the value of the forcePasswordChange property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.TrueFalse }
     *     
     */
    public void setForcePasswordChange(net.phonex.soap.entities.TrueFalse value) {
        this.forcePasswordChange = value;
    }
    /**
     * Gets the value of the storedFilesNum property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Integer }
     *     
     */
    public java.lang.Integer getStoredFilesNum() {
        return storedFilesNum;
    }

    /**
     * Sets the value of the storedFilesNum property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Integer }
     *     
     */
    public void setStoredFilesNum(java.lang.Integer value) {
        this.storedFilesNum = value;
    }
    /**
     * Gets the value of the serverTime property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getServerTime() {
        return serverTime;
    }

    /**
     * Sets the value of the serverTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setServerTime(java.util.Calendar value) {
        this.serverTime = value;
    }
    /**
     * Gets the value of the licenseType property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getLicenseType() {
        return licenseType;
    }

    /**
     * Sets the value of the licenseType property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setLicenseType(java.lang.String value) {
        this.licenseType = value;
    }
    /**
     * Gets the value of the accountIssued property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getAccountIssued() {
        return accountIssued;
    }

    /**
     * Sets the value of the accountIssued property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setAccountIssued(java.util.Calendar value) {
        this.accountIssued = value;
    }
    /**
     * Gets the value of the accountExpires property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getAccountExpires() {
        return accountExpires;
    }

    /**
     * Sets the value of the accountExpires property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setAccountExpires(java.util.Calendar value) {
        this.accountExpires = value;
    }
    /**
     * Gets the value of the firstAuthCheckDate property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getFirstAuthCheckDate() {
        return firstAuthCheckDate;
    }

    /**
     * Sets the value of the firstAuthCheckDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setFirstAuthCheckDate(java.util.Calendar value) {
        this.firstAuthCheckDate = value;
    }
    /**
     * Gets the value of the lastAuthCheckDate property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getLastAuthCheckDate() {
        return lastAuthCheckDate;
    }

    /**
     * Sets the value of the lastAuthCheckDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setLastAuthCheckDate(java.util.Calendar value) {
        this.lastAuthCheckDate = value;
    }
    /**
     * Gets the value of the firstLoginDate property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getFirstLoginDate() {
        return firstLoginDate;
    }

    /**
     * Sets the value of the firstLoginDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setFirstLoginDate(java.util.Calendar value) {
        this.firstLoginDate = value;
    }
    /**
     * Gets the value of the firstUserAddDate property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getFirstUserAddDate() {
        return firstUserAddDate;
    }

    /**
     * Sets the value of the firstUserAddDate property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setFirstUserAddDate(java.util.Calendar value) {
        this.firstUserAddDate = value;
    }
    /**
     * Gets the value of the accountLastActivity property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getAccountLastActivity() {
        return accountLastActivity;
    }

    /**
     * Sets the value of the accountLastActivity property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setAccountLastActivity(java.util.Calendar value) {
        this.accountLastActivity = value;
    }
    /**
     * Gets the value of the accountLastPassChange property.
     * 
     * @return
     *     possible object is
     *     {@link java.util.Calendar }
     *     
     */
    public java.util.Calendar getAccountLastPassChange() {
        return accountLastPassChange;
    }

    /**
     * Sets the value of the accountLastPassChange property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.util.Calendar }
     *     
     */
    public void setAccountLastPassChange(java.util.Calendar value) {
        this.accountLastPassChange = value;
    }
    /**
     * Gets the value of the accountDisabled property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.Boolean }
     *     
     */
    public java.lang.Boolean getAccountDisabled() {
        return accountDisabled;
    }

    /**
     * Sets the value of the accountDisabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.Boolean }
     *     
     */
    public void setAccountDisabled(java.lang.Boolean value) {
        this.accountDisabled = value;
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
        return 16;
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
                return this.forcePasswordChange == null ? null : this.forcePasswordChange.toString().toLowerCase();
            case 2:
                return this.storedFilesNum;
            case 3:
                return this.serverTime;
            case 4:
                return this.licenseType;
            case 5:
                return this.accountIssued;
            case 6:
                return this.accountExpires;
            case 7:
                return this.firstAuthCheckDate;
            case 8:
                return this.lastAuthCheckDate;
            case 9:
                return this.firstLoginDate;
            case 10:
                return this.firstUserAddDate;
            case 11:
                return this.accountLastActivity;
            case 12:
                return this.accountLastPassChange;
            case 13:
                return this.accountDisabled;
            case 14:
                return this.auxVersion;
            case 15:
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
                // type: int
                info.name = "errCode";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 1:
                // type: com.phoenix.soap.beans.TrueFalse
                info.name = "forcePasswordChange";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 2:
                // type: java.lang.Integer
                info.name = "storedFilesNum";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 3:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "serverTime";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 4:
                // type: java.lang.String
                info.name = "licenseType";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "accountIssued";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 6:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "accountExpires";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 7:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "firstAuthCheckDate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 8:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "lastAuthCheckDate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 9:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "firstLoginDate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 10:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "firstUserAddDate";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 11:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "accountLastActivity";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 12:
                // type: javax.xml.datatype.XMLGregorianCalendar
                info.name = "accountLastPassChange";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = java.util.Calendar.class;
                break;
            case 13:
                // type: java.lang.Boolean
                info.name = "accountDisabled";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 14:
                // type: java.lang.Integer
                info.name = "auxVersion";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.INTEGER_CLASS;
                break;
            case 15:
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
                // type: int
                this.errCode = Integer.parseInt(arg1.toString());
                break;
            case 1:
                // type: com.phoenix.soap.beans.TrueFalse
                this.forcePasswordChange = net.phonex.soap.entities.TrueFalse.fromValue((String) arg1);
                break;
            case 2:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.storedFilesNum = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.storedFilesNum = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("AccountInfoV1Response", e, "Problem with storedFilesNum parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("AccountInfoV1Response", "Problem with storedFilesNum parsing - unknown type");
               }
              
                break;
            case 3:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.serverTime = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_serverTime = (String) arg1; 
                    if (str1_serverTime==null || str1_serverTime.length()==0){ 
                        this.serverTime = null; 
                    } else { 
                        try { 
                            this.serverTime = DateUtils.stringToCalendar(str1_serverTime);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 4:
                // type: java.lang.String
                this.licenseType = (java.lang.String)arg1;
                break;
            case 5:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.accountIssued = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_accountIssued = (String) arg1; 
                    if (str1_accountIssued==null || str1_accountIssued.length()==0){ 
                        this.accountIssued = null; 
                    } else { 
                        try { 
                            this.accountIssued = DateUtils.stringToCalendar(str1_accountIssued);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 6:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.accountExpires = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_accountExpires = (String) arg1; 
                    if (str1_accountExpires==null || str1_accountExpires.length()==0){ 
                        this.accountExpires = null; 
                    } else { 
                        try { 
                            this.accountExpires = DateUtils.stringToCalendar(str1_accountExpires);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 7:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.firstAuthCheckDate = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_firstAuthCheckDate = (String) arg1; 
                    if (str1_firstAuthCheckDate==null || str1_firstAuthCheckDate.length()==0){ 
                        this.firstAuthCheckDate = null; 
                    } else { 
                        try { 
                            this.firstAuthCheckDate = DateUtils.stringToCalendar(str1_firstAuthCheckDate);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 8:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.lastAuthCheckDate = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_lastAuthCheckDate = (String) arg1; 
                    if (str1_lastAuthCheckDate==null || str1_lastAuthCheckDate.length()==0){ 
                        this.lastAuthCheckDate = null; 
                    } else { 
                        try { 
                            this.lastAuthCheckDate = DateUtils.stringToCalendar(str1_lastAuthCheckDate);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 9:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.firstLoginDate = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_firstLoginDate = (String) arg1; 
                    if (str1_firstLoginDate==null || str1_firstLoginDate.length()==0){ 
                        this.firstLoginDate = null; 
                    } else { 
                        try { 
                            this.firstLoginDate = DateUtils.stringToCalendar(str1_firstLoginDate);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 10:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.firstUserAddDate = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_firstUserAddDate = (String) arg1; 
                    if (str1_firstUserAddDate==null || str1_firstUserAddDate.length()==0){ 
                        this.firstUserAddDate = null; 
                    } else { 
                        try { 
                            this.firstUserAddDate = DateUtils.stringToCalendar(str1_firstUserAddDate);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 11:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.accountLastActivity = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_accountLastActivity = (String) arg1; 
                    if (str1_accountLastActivity==null || str1_accountLastActivity.length()==0){ 
                        this.accountLastActivity = null; 
                    } else { 
                        try { 
                            this.accountLastActivity = DateUtils.stringToCalendar(str1_accountLastActivity);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 12:
                // type: javax.xml.datatype.XMLGregorianCalendar
                if (arg1 instanceof java.util.Calendar) {
                    this.accountLastPassChange = (java.util.Calendar) arg1;
                } else if (arg1 instanceof java.lang.String) {
                    final String str1_accountLastPassChange = (String) arg1; 
                    if (str1_accountLastPassChange==null || str1_accountLastPassChange.length()==0){ 
                        this.accountLastPassChange = null; 
                    } else { 
                        try { 
                            this.accountLastPassChange = DateUtils.stringToCalendar(str1_accountLastPassChange);
                        } catch (Exception e) { 
                            Log.e("AccountInfoV1Response", "Problem with date parsing", e);
                        } 
                    } 
                } 
                
                break;
            case 13:
                // type: java.lang.Boolean
               if (arg1 instanceof Boolean) {
                   this.accountDisabled = (java.lang.Boolean) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.accountDisabled = Boolean.parseBoolean(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("AccountInfoV1Response", e, "Problem with accountDisabled parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("AccountInfoV1Response", "Problem with accountDisabled parsing - unknown type");
               }
              
                break;
            case 14:
                // type: java.lang.Integer
               if (arg1 instanceof Integer) {
                   this.auxVersion = (java.lang.Integer) arg1;
               } else if (arg1 instanceof java.lang.String) {
                   final String tmpArg1 = (String) arg1;
                   try {
                       this.auxVersion = Integer.parseInt(tmpArg1);
                   } catch (Exception e) {
                       Log.ef("AccountInfoV1Response", e, "Problem with auxVersion parsing, str=%s", tmpArg1);
                   }
               } else {
                   Log.e("AccountInfoV1Response", "Problem with auxVersion parsing - unknown type");
               }
              
                break;
            case 15:
                // type: java.lang.String
                this.auxJSON = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        new net.phonex.ksoap2.serialization.MarshalDate().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalCalendar().register(soapEnvelope);
        new net.phonex.soap.marshallers.MarshalInteger().register(soapEnvelope);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "accountInfoV1Response", net.phonex.soap.entities.AccountInfoV1Response.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "forcePasswordChange", net.phonex.soap.entities.TrueFalse.class); // same package
    } 

    @Override
    public String toString() {
        return "AccountInfoV1Response{"+"errCode=" + this.errCode+", forcePasswordChange=" + this.forcePasswordChange+", storedFilesNum=" + this.storedFilesNum+", serverTime=" + this.serverTime+", licenseType=" + this.licenseType+", accountIssued=" + this.accountIssued+", accountExpires=" + this.accountExpires+", firstAuthCheckDate=" + this.firstAuthCheckDate+", lastAuthCheckDate=" + this.lastAuthCheckDate+", firstLoginDate=" + this.firstLoginDate+", firstUserAddDate=" + this.firstUserAddDate+", accountLastActivity=" + this.accountLastActivity+", accountLastPassChange=" + this.accountLastPassChange+", accountDisabled=" + this.accountDisabled+", auxVersion=" + this.auxVersion+", auxJSON=" + this.auxJSON + '}';
    }
}
