package net.phonex.soap.entities;

import java.util.Hashtable;
import java.util.Vector;

import net.phonex.soap.SoapEnvelopeRegisterable;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.ksoap2.serialization.KvmSerializable;
import net.phonex.ksoap2.serialization.PropertyInfo;

public class FtAddDHKeysRequest extends Vector<net.phonex.soap.entities.FtDHKey> implements KvmSerializable, SoapEnvelopeRegisterable { 
    private static final long serialVersionUID = 1L;  // you can let the IDE generate this 
    // Whether to allow return null size from getPropertyCount().
    // If yes then de-serialization won't work.
    private boolean allowReturnNullSize = false;   

    /**
     * Gets the value of the allowReturnNullSize property.
     * 
     * @return
     *     possible object is
     *     {@link boolean }
     *     
     */
    public boolean getAllowReturnNullSize() {
        return allowReturnNullSize;
    }

    /**
     * Sets the value of the allowReturnNullSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link boolean }
     *     
     */
    public void setAllowReturnNullSize(boolean value) {
        this.allowReturnNullSize = value;
    }

    @Override
    public Object getProperty(int index) {
        return this.get(index);
    }

    @Override 
    public int getPropertyCount() { 
        int i = this.size(); 
        if (i==0 && this.allowReturnNullSize==false) return 1; 
        return i;
    } 

    @Override 
    public void setProperty(int index, Object value) { 
        this.add((net.phonex.soap.entities.FtDHKey) value); 
    } 

    @Override 
    public void getPropertyInfo(int index, Hashtable properties, PropertyInfo info) { 
        info.name = "dhkeys"; 
        info.type = net.phonex.soap.entities.FtDHKey.class; 
        info.setNamespace(net.phonex.soap.ServiceConstants.NAMESPACE); 
    } 

    @Override 
    public void register(SoapSerializationEnvelope soapEnvelope) { 
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "FtDHKey", net.phonex.soap.entities.FtDHKey.class);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "dhkeys", net.phonex.soap.entities.FtDHKey.class);
        soapEnvelope.addMapping(net.phonex.soap.ServiceConstants.NAMESPACE, "ftAddDHKeysRequest", net.phonex.soap.entities.FtAddDHKeysRequest.class);

        new FtDHKey().register(soapEnvelope);
    }
}
