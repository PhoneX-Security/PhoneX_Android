package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class ContactlistChangeRequestElement implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String targetUser;
    protected net.phonex.soap.entities.UserIdentifier user;
    protected net.phonex.soap.entities.ContactlistAction action;
    protected net.phonex.soap.entities.WhitelistAction whitelistAction;
    protected java.lang.String displayName;


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
     * Gets the value of the user property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.UserIdentifier }
     *     
     */
    public net.phonex.soap.entities.UserIdentifier getUser() {
        return user;
    }

    /**
     * Sets the value of the user property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.UserIdentifier }
     *     
     */
    public void setUser(net.phonex.soap.entities.UserIdentifier value) {
        this.user = value;
    }
    /**
     * Gets the value of the action property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.ContactlistAction }
     *     
     */
    public net.phonex.soap.entities.ContactlistAction getAction() {
        return action;
    }

    /**
     * Sets the value of the action property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.ContactlistAction }
     *     
     */
    public void setAction(net.phonex.soap.entities.ContactlistAction value) {
        this.action = value;
    }
    /**
     * Gets the value of the whitelistAction property.
     * 
     * @return
     *     possible object is
     *     {@link net.phonex.soap.entities.WhitelistAction }
     *     
     */
    public net.phonex.soap.entities.WhitelistAction getWhitelistAction() {
        return whitelistAction;
    }

    /**
     * Sets the value of the whitelistAction property.
     * 
     * @param value
     *     allowed object is
     *     {@link net.phonex.soap.entities.WhitelistAction }
     *     
     */
    public void setWhitelistAction(net.phonex.soap.entities.WhitelistAction value) {
        this.whitelistAction = value;
    }
    /**
     * Gets the value of the displayName property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the value of the displayName property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setDisplayName(java.lang.String value) {
        this.displayName = value;
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
        return 5;
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
                return this.targetUser;
            case 1:
                return this.user;
            case 2:
                return this.action == null ? null : this.action.toString().toLowerCase();
            case 3:
                return this.whitelistAction == null ? null : this.whitelistAction.toString().toLowerCase();
            case 4:
                return this.displayName;
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
                info.name = "targetUser";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 1:
                // type: com.phoenix.soap.beans.UserIdentifier
                info.name = "user";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = net.phonex.soap.entities.UserIdentifier.class;
                break;
            case 2:
                // type: com.phoenix.soap.beans.ContactlistAction
                info.name = "action";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 3:
                // type: com.phoenix.soap.beans.WhitelistAction
                info.name = "whitelistAction";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = PropertyInfo.STRING_CLASS;
                break;
            case 4:
                // type: java.lang.String
                info.name = "displayName";
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
                this.targetUser = (java.lang.String)arg1;
                break;
            case 1:
                // type: com.phoenix.soap.beans.UserIdentifier
                this.user = (net.phonex.soap.entities.UserIdentifier)arg1;
                break;
            case 2:
                // type: com.phoenix.soap.beans.ContactlistAction
                this.action = net.phonex.soap.entities.ContactlistAction.fromValue((String) arg1);
                break;
            case 3:
                // type: com.phoenix.soap.beans.WhitelistAction
                this.whitelistAction = net.phonex.soap.entities.WhitelistAction.fromValue((String) arg1);
                break;
            case 4:
                // type: java.lang.String
                this.displayName = (java.lang.String)arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "whitelistAction", net.phonex.soap.entities.WhitelistAction.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "action", net.phonex.soap.entities.ContactlistAction.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "user", net.phonex.soap.entities.UserIdentifier.class); // same package
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "UserIdentifier", net.phonex.soap.entities.UserIdentifier.class); // package i class
        new net.phonex.soap.entities.UserIdentifier().register(soapEnvelope); // registerable 
    } 

    @Override
    public String toString() {
        return "ContactlistChangeRequestElement{"+"targetUser=" + this.targetUser+", user=" + this.user+", action=" + this.action+", whitelistAction=" + this.whitelistAction+", displayName=" + this.displayName + '}';
    }
}
