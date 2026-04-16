package io.kairo.api;

import java.lang.annotation.*;

/**
 * Marks APIs that are experimental and may change without notice in future versions.
 *
 * <p>Experimental APIs are functional but their signatures, behavior, or existence may change
 * between minor versions. Do not depend on them in stable production code.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Experimental {
    /** Optional description of why this API is experimental. */
    String value() default "";
}
