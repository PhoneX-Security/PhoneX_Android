package net.phonex.soap.marshallers;

import net.phonex.ksoap2.isodate.IsoDate;
import net.phonex.ksoap2.serialization.Marshal;
import net.phonex.ksoap2.serialization.PropertyInfo;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.util.DateUtils;
import net.phonex.util.Log;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.util.Calendar;

/**
 * Calendar marshaler.
 * 
 * @author ph4r05
 */
public class MarshalCalendar implements Marshal {
	private static final String TAG = "MarshalCalendar"; 
	
	public Object readInstance(XmlPullParser parser, String namespace, String name, PropertyInfo expected) throws IOException, XmlPullParserException {
		 try {
			 return DateUtils.stringToCalendar(parser.nextText());
		 } catch(Exception ex){
			 Log.e(TAG, "Calendar parsing exception", ex);
		 }
		 
		 return null;
     }

     public void register(SoapSerializationEnvelope cm) {
          cm.addMapping(cm.xsd, "DateTime", java.util.Calendar.class, this);
          cm.addMapping(cm.xsd, "DateTime", java.util.GregorianCalendar.class, this);
     }

     public void writeInstance(XmlSerializer writer, Object obj) throws IOException {
    	 final Calendar c = (Calendar) obj;
         writer.text(IsoDate.dateToString(c.getTime(), IsoDate.DATE_TIME));
     }
}
