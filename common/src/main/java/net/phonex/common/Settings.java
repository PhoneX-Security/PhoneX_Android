package net.phonex.common;

/**
 * We cannot use BuildConfig.DEBUG in library modules for now, therefore this switch is checked/on of by quilt patch depending on a release
 * Created by miroc on 11.9.15.
 */
public final class Settings {
    public static boolean debuggingRelease(){
        // TODO switch in patch file
        return true;
    }
}
