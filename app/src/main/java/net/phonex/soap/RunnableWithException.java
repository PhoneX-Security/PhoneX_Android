package net.phonex.soap;

/**
 * Created by miroc on 31.8.15.
 */
public interface RunnableWithException extends Runnable{
    Exception getThrownException();
}
