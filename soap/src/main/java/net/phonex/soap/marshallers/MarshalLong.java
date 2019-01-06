package net.phonex.soap.marshallers;

import net.phonex.ksoap2.serialization.Marshal;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;

public class MarshalLong  implements Marshal {
	public Object readInstance(XmlPullParser parser, String namespace, String name, PropertyInfo expected) throws IOException, XmlPullParserException {
        return Long.parseLong(parser.nextText());
    }

    public void register(SoapSerializationEnvelope cm) {
         cm.addMapping(cm.xsd, "Long", Long.class, this);
    }

    public void writeInstance(XmlSerializer writer, Object obj) throws IOException {
    	final Long i = (Long) obj;
    	if (i==null){
    		writer.text("");
    	} else {
    		writer.text(i.toString());
    	}
    }
}
