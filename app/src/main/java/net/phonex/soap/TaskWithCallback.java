package net.phonex.soap;

/**
 * Handy runnable executing RunnableWithException with callback
 * Created by miroc on 30.8.15.
 */
public class TaskWithCallback implements Runnable {
    private final RunnableWithException runnable;
    private final Callback callback;

    public TaskWithCallback(RunnableWithException runnable, Callback callback) {
        this.runnable = runnable;
        this.callback = callback;
    }

    @Override
    public void run() {
        runnable.run();
        if (callback != null){
            Exception exception = runnable.getThrownException();
            if (exception != null){
                callback.onFailed(exception);
            } else {
                callback.onCompleted();
            }
        }
    }

    public interface Callback {
        void onCompleted();
        void onFailed(Exception e);
    }
}
