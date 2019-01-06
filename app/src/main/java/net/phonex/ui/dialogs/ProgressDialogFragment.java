package net.phonex.ui.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;

import com.afollestad.materialdialogs.MaterialDialog;

import net.phonex.R;
import net.phonex.soap.BaseAsyncProgress;
import net.phonex.soap.BaseAsyncTask;
import net.phonex.util.Log;

public class ProgressDialogFragment extends DialogFragment implements DialogInterface.OnKeyListener{
	

	private static final String THIS_FILE = "ProgressDialogFragment";
	//saving progress values because of the changingConfiguration
	private int progress = 0;
	private String message = "";
	private String title = null;
	private boolean canClose = true;
	private boolean indeterminate = false;
	private boolean cancelable = true;

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // disable the back button
            return true;
        }
        return false;
    }

	public void setIndeterminate(boolean indeterminate) {
		this.indeterminate = indeterminate;
	}


	public boolean isCancelable() {
		return cancelable;
	}


	public void setCancelable(boolean cancelable) {
		this.cancelable = cancelable;
	}


	/**
	 * how many checkpoints on the progress bar we have
	 */
	private int checkpointsNumber = 5; //default is 5 points 
	
	/**
	 * Task that currently owns this progress dialog (updates it).
	 * This value may be null.
	 */
	private BaseAsyncTask<?> mTask;
	
	public BaseAsyncTask<?> getTask() {
		return mTask;
	}
	
	public void setTask(BaseAsyncTask<?> mTask) {
		this.mTask = mTask;
		 // Tell the AsyncTask to call updateProgress() and taskFinished() on this fragment.
        if (mTask!=null)
		    mTask.setDialogFragment(this);
	}


	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		if (getDialog()==null){
			
			Log.i(THIS_FILE,"onCreateDialog");

             MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity())
                    .content(message)
                    .progress(true, 0)
                    .negativeText(R.string.cancel);

			if (title != null){
				builder.title(title);
			}

            if (cancelable){
                builder.callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        ProgressDialogFragment.this.dismiss();
                    }
                });
            }

            MaterialDialog dialog = builder.build();
            dialog.setOnKeyListener(this);
            dialog.setCanceledOnTouchOutside(false);
            return dialog;
		}
		return getDialog();		
    }
	
	@Override
	public void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		Log.i(THIS_FILE,"onCreate");
		// Retain this instance so it isn't destroyed  when changing the orientation
		setRetainInstance(true);			
	}
	
	@Override
	public void onDismiss(DialogInterface dialog) {		
		super.onDismiss(dialog);
		
		// If true, the thread is interrupted immediately, which may do bad things.
        // If false, it guarantees a result is never returned (onPostExecute() isn't called)
        // but you have to repeatedly call isCancelled() in your doInBackground()
        // function to check if it should exit. For some tasks that might not be feasible.
		if (mTask != null){
			mTask.cancel(false);
		}
	}
	
	public void setProgress(int arg1) {
		progress = arg1;
        
    }
	
	public void setTitle(String title){
		this.title =title;
        MaterialDialog d = (MaterialDialog) getDialog();
        if (d!=null){
            d.setTitle(title);
        }
	}

	public void setMessage(String message){
		this.message = message;
        MaterialDialog d = (MaterialDialog) getDialog();
        if (d!=null){
            d.setContent(message);
        }
	}
	
	public int getMax()	{
		return 100;
		
	}
	
	// This is to work around what is apparently a bug. If you don't have it
    // here the dialog will be dismissed on rotation, so tell it not to dismiss.
    @Override
    public void onDestroyView()
    {
    	Log.i(THIS_FILE,"onDestroyView");
        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }
    
    
    

 // This is also called by the AsyncTask.
    public void taskFinished(Exception result)
    {	
        // Make sure we check if it is resumed because we will crash if trying to dismiss the dialog
        // after the user has switched to another app.
        if (isResumed()){
        	
        	if (result!=null){
        		dismiss();	
        	} else if (canClose){
        		setProgress(100);
        		setMessage("Done");        		
        		dismiss();
        	}
        	else{
        		return;        		
        	}
        	
        }
        mTask = null;

    }

    public void updateProgress() {
		updateProgress(1);
	}
    
	public void updateProgress(int delta) {
		int newProgress = progress + delta*getMax() / checkpointsNumber ;
		if(newProgress > getMax())  newProgress= getMax();
		setProgress(newProgress);
	}
	
	public void updateProgress(String msg){
		updateProgress();
		setMessage(msg);
	}
	
	public void updateProgress(BaseAsyncProgress msg){
		// Is modification of total number steps required?
		if (msg.getDeltaTotalSteps()!=0){
			checkpointsNumber += msg.getDeltaTotalSteps();
		}
		
		// If indeterminate is not null
		if (msg.getIndeterminate()!=null){
			final boolean indeterminate = Boolean.TRUE.equals(msg.getIndeterminate()); 
			setIndeterminate(indeterminate);
			setIndeterminateInternal(indeterminate);
			if (!indeterminate){
				// Move progress bar with given number of steps.
				updateProgress(msg.getDeltaStep());
			}
			
		} else {
			// Move progress bar with given number of steps.
			updateProgress(msg.getDeltaStep());
		}
		
		// Text message to set.
		// If message is null it stays same as before.
		if (msg.getMessage()!=null){
			setMessage(msg.getMessage());
		}
	}
	
	public void setIndeterminateInternal(boolean indeterminate){
		// turned off
	}


	public void setCheckpointsNumber(int checkpointsNumber) {
		this.checkpointsNumber = checkpointsNumber;
	}

	public void setCanClose(boolean canClose) {
		this.canClose = canClose;
	}
	
}