package net.phonex.soap;

/**
 * Class to indicate progress change in task. 
 * @author ph4r05
 *
 */
public class BaseAsyncProgress {
	
	/**
	 * Progress message to display.
	 */
	private String message;
	
	/**
	 * delta step to add to current progress.
	 */
	private int deltaStep=1;
	
	/**
	 * How many steps to add to final progress by this step.
	 * Used rarely, use case: if special condition branch is taken, we may need
	 * more steps to go through.
	 */
	private int deltaTotalSteps=0;
	
	/**
	 * If not null, changes progress to indeterminate as this field indicates.
	 */
	private Boolean indeterminate=null;
	
	public BaseAsyncProgress() {
	}
	
	public BaseAsyncProgress(String message) {
		super();
		this.message = message;
	}

	public BaseAsyncProgress(String message, int deltaStep) {
		super();
		this.message = message;
		this.deltaStep = deltaStep;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * @param message the message to set
	 */
	public BaseAsyncProgress setMessage(String message) {
		this.message = message;
		return this;
	}

	/**
	 * @return the deltaStep
	 */
	public int getDeltaStep() {
		return deltaStep;
	}

	/**
	 * @param deltaStep the deltaStep to set
	 */
	public BaseAsyncProgress setDeltaStep(int deltaStep) {
		this.deltaStep = deltaStep;
		return this;
	}

	/**
	 * @return the deltaTotalSteps
	 */
	public int getDeltaTotalSteps() {
		return deltaTotalSteps;
	}

	/**
	 * @param deltaTotalSteps the deltaTotalSteps to set
	 */
	public BaseAsyncProgress setDeltaTotalSteps(int deltaTotalSteps) {
		this.deltaTotalSteps = deltaTotalSteps;
		return this;
	}

	public Boolean getIndeterminate() {
		return indeterminate;
	}

	public BaseAsyncProgress setIndeterminate(Boolean indeterminate) {
		this.indeterminate = indeterminate;
		return this;
	}
	
}
