/**
 * 
 */
package net.phonex.soap.entities;

import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;

import java.util.Hashtable;

//
//This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-2 
//See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
//Any modifications to this file will be lost upon recompilation of the source schema. 
//Generated on: 2012.12.14 at 08:32:31 PM CET 
//

/**
 * <p>
 * Java class for anonymous complex type.
 * 
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="user" type="{http://phoenix.com/hr/schemas}userSIP"/>
 *         &lt;element name="userToken" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
public class GetOneTimeTokenRequest implements KvmSerializable {
	public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
	
	protected String user;
	protected String userToken;
	protected int type;

	/**
	 * Gets the value of the user property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets the value of the user property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
	public void setUser(String value) {
		this.user = value;
	}

	/**
	 * Gets the value of the userToken property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	public String getUserToken() {
		return userToken;
	}

	/**
	 * Sets the value of the userToken property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
	public void setUserToken(String value) {
		this.userToken = value;
	}

	/**
	 * Gets the value of the type property.
	 * 
	 */
	public int getType() {
		return type;
	}

	/**
	 * Sets the value of the type property.
	 * 
	 */
	public void setType(int value) {
		this.type = value;
	}

	@Override
	public Object getProperty(int index) {
		 switch (index){
		    case 0:
		        return user;
		    case 1:
		        return userToken;
		    case 2:
		    	return this.type;
		    default:
		         return null;
		    }
	}

	@Override
	public int getPropertyCount() {
		return 3;
	}

	@Override
	public void getPropertyInfo(int index, Hashtable arg1, PropertyInfo info) {
		switch(index) {
	    case 0:
	        info.type = null;
	        info.name = "user";
	        info.setNamespace(NAMESPACE);
	        break;
	    case 1:
	        info.type = null;
	        info.name = "userToken";
	        info.setNamespace(NAMESPACE);
	        break;
	    case 2:
	        info.type = null;
	        info.name = "type";
	        info.setNamespace(NAMESPACE);
	        break;
	    default:
	        break;
	    }
		
	}

	@Override
	public void setProperty(int index, Object arg1) {
		switch (index){
	    case 0:
	        this.user = (String) arg1;
	        break;
	    case 1:
	        this.userToken = (String) arg1;
	        break;
	    case 2:
	    	this.type = Integer.valueOf(arg1.toString());
	    	break;
	    default:
	         return;
	    }
	}

	@Override
	public String toString() {
		return "GetOneTimeTokenRequest [user=" + user + ", userToken="
				+ userToken + ", type=" + type + "]";
	}
}