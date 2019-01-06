/**
 * 
 */
package net.phonex.soap;

import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;

/**
 * @author ph4r05
 *
 */
public interface SoapEnvelopeRegisterable {
	void register(SoapSerializationEnvelope soapEnvelope);
}
