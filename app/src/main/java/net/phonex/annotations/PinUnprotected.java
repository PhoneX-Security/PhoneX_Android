package net.phonex.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Activities to mark those that shouldn't be PinLock protected (e.g. IntroActivity)
 * Created by miroc on 11.12.14.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PinUnprotected {
}
