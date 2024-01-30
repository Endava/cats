package com.endava.cats.model.ann;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

/**
 * Gson exclusion strategy that excludes fields and classes annotated with {@code Exclude}.
 */
public class ExcludeTestCaseStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        return clazz.getAnnotation(Exclude.class) != null;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return f.getAnnotation(Exclude.class) != null;
    }
}