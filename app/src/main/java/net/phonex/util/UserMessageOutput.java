package net.phonex.util;

import org.spongycastle.mail.smime.SMIMESigned;

/**
 * Simple class to represent result after decrypting and verifying
 * @author ph4r05
 *
 */
public class UserMessageOutput {
	// message meets our standards
	private boolean messageCorrect=false;
	private boolean textPartValid=false;
	private String textPart=null;
	private Integer randNum=null;
	private Long sendDate=null;
	private String sourceSip=null;
	
	private SMIMESigned signedEnvelope=null;
	private Object decompressedObject=null;
	
	private boolean veryfiedSuccessfully=false;

    private boolean hmacValid;

	public UserMessageOutput() {
		super();
	}

	public boolean isTextPartValid() {
		return textPartValid;
	}

	public void setTextPartValid(boolean textPartValid) {
		this.textPartValid = textPartValid;
	}

	public String getTextPart() {
		return textPart;
	}

	public void setTextPart(String textPart) {
		this.textPart = textPart;
	}

	public SMIMESigned getSignedEnvelope() {
		return signedEnvelope;
	}

	public void setSignedEnvelope(SMIMESigned signedEnvelope) {
		this.signedEnvelope = signedEnvelope;
	}

	public Object getDecompressedObject() {
		return decompressedObject;
	}

	public void setDecompressedObject(Object decompressedObject) {
		this.decompressedObject = decompressedObject;
	}

	public boolean isVerifiedSuccessfully() {
		return veryfiedSuccessfully;
	}

	public void setVerifiedSuccessfully(boolean veryfiedSuccessfully) {
		this.veryfiedSuccessfully = veryfiedSuccessfully;
	}
	
	public boolean isMessageCorrect() {
		return messageCorrect;
	}

	public void setMessageCorrect(boolean messageCorrect) {
		this.messageCorrect = messageCorrect;
	}

	public Integer getRandNum() {
		return randNum;
	}

	public void setRandNum(Integer randNum) {
		this.randNum = randNum;
	}

	public Long getSendDate() {
		return sendDate;
	}

	public void setSendDate(Long sendDate) {
		this.sendDate = sendDate;
	}

	public String getSourceSip() {
		return sourceSip;
	}

	public void setSourceSip(String sourceSip) {
		this.sourceSip = sourceSip;
	}

    public boolean isHmacValid() {
        return hmacValid;
    }

    public void setHmacValid(boolean hmacValid) {
        this.hmacValid = hmacValid;
    }

    @Override
    public String toString() {
        return "UserMessageOutput{" +
                "messageCorrect=" + messageCorrect +
                ", textPartValid=" + textPartValid +
                ", textPart='" + textPart + '\'' +
                ", randNum=" + randNum +
                ", sendDate=" + sendDate +
                ", from='" + sourceSip + '\'' +
                ", signedEnvelope=" + signedEnvelope +
                ", decompressedObject=" + decompressedObject +
                ", veryfiedSuccessfully=" + veryfiedSuccessfully +
                ", hmacValid=" + hmacValid +
                '}';
    }
}
