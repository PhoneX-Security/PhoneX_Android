package net.phonex.soap;

import net.phonex.ksoap2.serialization.SoapObject;
import net.phonex.ksoap2.serialization.SoapSerializationEnvelope;
import net.phonex.soap.entities.GetOneTimeTokenRequest;
import net.phonex.soap.entities.GetOneTimeTokenResponse;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class Marshaller {
	
	/**
	 * Creates soap object 
	 * @param req
	 */
	public static void marshall(GetOneTimeTokenRequest req, SoapObject sob, SoapSerializationEnvelope env){
        sob.addProperty("user", req.getUser());
		sob.addProperty("userToken", req.getUserToken());
        sob.addProperty("type", req.getType());
	}
	
	/**
	 * Unmarshall soap object received to given response
	 * @param req
	 * @throws UnmarshallingException 
	 * @throws ParseException 
	 */
	public static void unmarshall(GetOneTimeTokenResponse req, SoapObject sob, SoapSerializationEnvelope env) throws UnmarshallingException, ParseException{
		/**
		 * @XmlType(name = "", propOrder = {
		    "user",
		    "userToken",
		    "serverToken",
		    "notValidAfter"
		})
		 */
		int totalCount = sob.getPropertyCount();
		if (totalCount!=4){
			throw new UnmarshallingException("Invalid count of fields");
		}
		
		req.setUser(sob.getPropertyAsString("user"));
		req.setUserToken(sob.getPropertyAsString("userToken"));
		req.setServerToken(sob.getPropertyAsString("serverToken"));
		String dateString = sob.getPropertyAsString("notValidAfter");
		
		DateFormat formatter = new SimpleDateFormat("MM/dd/yy");
		Date date = formatter.parse(dateString);
		
		req.setNotValidAfter(date);
	}
	
}
