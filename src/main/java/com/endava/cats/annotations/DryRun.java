package com.endava.cats.annotations;

import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used by Quarkus interceptors to suppress calls to the annotated methods during {@code --dryRun}.
 */
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DryRun {
}
