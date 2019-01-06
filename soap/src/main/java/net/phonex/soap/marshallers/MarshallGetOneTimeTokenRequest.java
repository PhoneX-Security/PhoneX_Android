package net.phonex.soap.marshallers;

import net.phonex.ksoap2.serialization.Marshal;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.entities.GetOneTimeTokenRequest;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;


/**
 * Working marshaler for GetOneTimeTokenRequest - takes namespace into account
 * @author ph4r05
 *
 */
public class MarshallGetOneTimeTokenRequest implements Marshal {
	
	public final static String NAMESPACE = "http://phoenix.com/hr/schemas";
	
	@Override
	public Object readInstance(XmlPullParser arg0, String arg1, String arg2, PropertyInfo arg3) throws IOException, XmlPullParserException {
		throw new UnsupportedOperationException("Not implemented yet!");
	}

	@Override
	public void writeInstance(XmlSerializer arg0, Object arg1) throws IOException {
		if (GetOneTimeTokenRequest.class.isInstance(arg1)==false){
			throw new IllegalArgumentException("Cannot marshall this class");
		}
		
		final GetOneTimeTokenRequest req = (GetOneTimeTokenRequest) arg1;
		
		arg0.setPrefix("sch", NAMESPACE);
		arg0.startTag(NAMESPACE, "user");
		arg0.text(req.getUser());
		arg0.endTag(NAMESPACE, "user");
		
		arg0.setPrefix("sch", NAMESPACE);
		arg0.startTag(NAMESPACE, "userToken");
		arg0.text(req.getUserToken());
		arg0.endTag(NAMESPACE, "userToken");
		
		arg0.setPrefix("sch", NAMESPACE);
		arg0.startTag(NAMESPACE, "type");
		arg0.text(String.valueOf(req.getType()));
		arg0.endTag(NAMESPACE, "type");	
	}
	
	@Override
	public void register(SoapSerializationEnvelope cm) {
		cm.addMapping(NAMESPACE, "getOneTimeTokenRequest", GetOneTimeTokenRequest.class, this);
	}
}
