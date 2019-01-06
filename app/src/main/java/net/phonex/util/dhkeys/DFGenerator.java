package net.phonex.util.dhkeys;

import android.content.ContentResolver;
import android.content.Context;

import net.phonex.db.entity.DHOffline;
import net.phonex.util.Base64;
import net.phonex.util.Log;

import org.spongycastle.asn1.ASN1Encodable;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.ASN1SequenceParser;
import org.spongycastle.asn1.ASN1StreamParser;
import org.spongycastle.openssl.PEMReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import javax.crypto.spec.DHParameterSpec;


public class DFGenerator {
	// provider name
    private static final String BC = org.spongycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    
	// SpongyCastle static initialization
    static {
    	//Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
    	//Provider p = new BouncyCastleProvider(); 
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(),1);
    }
		
	public static final String TAG = "DFGenerator";
	private Context context;
	
	public DFGenerator(Context ctx) {
		this.context = ctx;
	}
	
	/**
	 * generate and store new DH key pair for particular sip user in contact list
	 * @param sip
	 * @return true on success, otherwise false
	 * @throws Exception 
	 */
	public void generate(String sip) throws Exception{		
		SecureRandom rand = new SecureRandom();
		// we randomly pick up a prime group (from pregenerated set) from which DH parameters are derived
		int groupId = rand.nextInt(256) + 1;		
		DHParameterSpec dhParameterSpec = loadDHParameterSpec(groupId);
		if (dhParameterSpec==null){
			throw new Exception("cannot load dhParameters");
		}		

		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DH", BC);
		keyGen.initialize(dhParameterSpec, rand);
		
		// Generate DH key			
		KeyPair aPair = keyGen.generateKeyPair();			
			
		// Store to DB
		DHOffline data = new DHOffline();
		data.setPrivateKey(Base64.encodeBytes(aPair.getPrivate().getEncoded()));
		data.setPublicKey(Base64.encodeBytes(aPair.getPublic().getEncoded()));
		data.setGroupNumber(groupId);
		data.setSip(sip);
		data.setDateCreated(new Date());
			
		ContentResolver cr = context.getContentResolver();
		cr.insert(DHOffline.DH_OFFLINE_URI, data.getDbContentValues());
		Log.vf(TAG, "Stored DH object to database; detail=[%s]", data.toString());						
			
			// two party agreement
			// KeyAgreement aKeyAgree = KeyAgreement.getInstance("DH", BC);
			// aKeyAgree.init(aPair.getPrivate()); 
			// bKeyAgree.init(bPair.getPrivate());  
			// aKeyAgree.doPhase(bPair.getPublic(), true); 
			// bKeyAgree.doPhase(aPair.getPublic(), true);
	}
	
	/**
	 * load DH PARAMETER from file assets/dh_groups/dhparam_4096_1_0<groupNumber>.pem
	 * @param groupNumber should be between 001-256
	 * @return
	 */
	public DHParameterSpec loadDHParameterSpec(int groupNumber){
		try {			
						
			InputStream is = null;
			is = context.getAssets().open("dh_groups/dhparam_4096_1_0" + String.format("%03d", groupNumber) + ".pem");
	        
	        PEMReader pr = new PEMReader(new InputStreamReader(is));
			ASN1StreamParser asn1StreamParser = new ASN1StreamParser(pr.readPemObject().getContent());

			Object obj = asn1StreamParser.readObject();			
			DHParameterSpec spec = null;
			
			if (obj instanceof ASN1SequenceParser){
				// DH_PARAMETERS contains SEQUENCE of two INTEGERS (p and g, optionally also l)
                ASN1Sequence asn1Sequence = ASN1Sequence.getInstance(obj);
                ASN1Encodable pAsn= asn1Sequence.getObjectAt(0); // prime
                ASN1Encodable gAsn= asn1Sequence.getObjectAt(1); // generator
                if (pAsn==null || gAsn ==null){
                	Log.e(TAG, "cannot load DH parameters from PEM file, ASN1 sequence doesn't contain proper data.");                	
                } else {
                	BigInteger p = ASN1Integer.getInstance(pAsn.toASN1Primitive()).getValue();
                    BigInteger g = ASN1Integer.getInstance(gAsn.toASN1Primitive()).getValue();
                    spec = new DHParameterSpec(p, g);
                }
            }
			pr.close();
	        is.close();			
	        return spec;
		} catch (Exception e) {
			Log.e(TAG, "Problem while working with dhparam assets file", e);
			return null;
		}		
	}
	
	public static void test(Context context){
//		DFGenerator dfg = new DFGenerator(context);
//		dfg.generate();
	}
}
