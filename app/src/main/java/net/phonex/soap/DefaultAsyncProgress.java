package net.phonex.soap;

/**
 * Simple class to publish progress in certificate signing async task.
 * @author ph4r05
 */
public class DefaultAsyncProgress {
	private double percent;
	private String message;
	
	public DefaultAsyncProgress(double percent, String message) {
		this.percent = percent;
		this.message = message;
	}
	
	@Override
	public String toString() {
		return "DefaultAsyncProgress [percent=" + percent + ", message=" + message + "]";
	}
	
	public double getPercent() {
		return percent;
	}
	public void setPercent(double percent) {
		this.percent = percent;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	
	
}
