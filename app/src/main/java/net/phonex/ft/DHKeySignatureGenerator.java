package net.phonex.ft;

import net.phonex.pub.proto.FileTransfer.DhkeySig;
import net.phonex.util.crypto.CryptoHelper;
import net.phonex.util.crypto.CryptoHelper.SignatureException;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

public class DHKeySignatureGenerator {
    
    /**
     * Generates digital signature for the DhKey.
     * @param tosign
     * @param privKey
     * @param rand
     * @return
     * @throws SignatureException
     */
	public static byte[] generateDhKeySignature(DhkeySig tosign, PrivateKey privKey, SecureRandom rand) throws SignatureException{
		try {
			// 1. Serialize given structure
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			tosign.writeTo(bos);
			
			byte[] serialized = bos.toByteArray();
			bos.close();
			
			// 2. sign
			byte[] signature = CryptoHelper.sign(serialized, privKey, rand);
			return signature;
			
		} catch(Exception ex){
			throw new SignatureException("Exception during creating signature", ex);
		}
	}
	
	/**
	 * Signature verification for the DhKey.
	 * @param toverify
	 * @param signature
	 * @param pubKey
	 * @return
	 * @throws SignatureException
	 */
	public static boolean verifyDhKeySIgnature(DhkeySig toverify, byte[] signature, PublicKey pubKey) throws SignatureException{
		try {
			// 1. Serialize given structure
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			toverify.writeTo(bos);
			
			byte[] serialized = bos.toByteArray();
			bos.close();
			
			// 2. verify
			return CryptoHelper.verify(serialized, signature, pubKey);			
		} catch(Exception ex){
			throw new SignatureException("Exception during creating signature", ex);
		}
	}
	
	
}
