package net.phonex.soap.entities;

import net.phonex.soap.SoapEnvelopeRegisterable;
import java.util.Hashtable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;


public class ContactlistGetRequest implements KvmSerializable, SoapEnvelopeRegisterable {

    public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
    protected boolean ignoreNullWrappers = false;
    protected java.lang.String targetUser;
    protected UserIdentifierVectorSerializer users;


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
     * Gets the value of the users property.
     * 
     * @return
     *     possible object is
     *     {@link UserIdentifierVectorSerializer }
     *     
     */
    public UserIdentifierVectorSerializer getUsers() {
        return users;
    }

    /**
     * Sets the value of the users property.
     * 
     * @param value
     *     allowed object is
     *     {@link UserIdentifierVectorSerializer }
     *     
     */
    public void setUsers(UserIdentifierVectorSerializer value) {
        this.users = value;
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
        int length = 2;
        if (this.ignoreNullWrappers==false) return length;
        if (this.users==null) length-=1;
        return length; 
    } 


    /**
     * Computes index shift for serialization methods in order to ignore null
     * wrappers during serialization so as not to produce lists with one empty
     * instance
     */
     protected int ignoreNullWrapperShift(int idx) {
        int i = idx;
        if (this.ignoreNullWrappers==false) return i;
        if (i==1 && this.users==null) i+=1;
        return i;
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
                return this.users;
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
                // type: java.util.List
                info.name = "users";
                info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE);
                info.type = UserIdentifierVectorSerializer.class;
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
                // type: java.util.List
                this.users = (UserIdentifierVectorSerializer) arg1;
                break;
            default:
                return;
        }
    }

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "contactlistGetRequest", net.phonex.soap.entities.ContactlistGetRequest.class); // root
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "UserIdentifierVectorSerializer", UserIdentifierVectorSerializer.class); // wrapper 
        new UserIdentifierVectorSerializer().register(soapEnvelope); // vectorizer 
    } 

    @Override
    public String toString() {
        return "ContactlistGetRequest{"+"targetUser=" + this.targetUser+", users=" + this.users + '}';
    }
}
