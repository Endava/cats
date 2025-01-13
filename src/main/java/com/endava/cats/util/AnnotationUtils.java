package com.endava.cats.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Minimal replacement for the Spring AnnotationUtils calls used in CATS.
 * <p>
 * Supports:
 * - isAnnotationDeclaredLocally(): only direct presence
 * - findAnnotation(): direct + meta-annotations; optionally scans superclasses and interfaces
 * <p>
 * Notes:
 * - This is intentionally small and safe for native-image.
 * - It avoids deep Spring semantics like @AliasFor.
 */
public abstract class AnnotationUtils {

    private AnnotationUtils() {
        // utility
    }

    /**
     * Equivalent to Spring's AnnotationUtils.isAnnotationDeclaredLocally(...).
     *
     * @param annotationType the annotation type to check
     * @param targetType     the target class to scan
     * @return true if the annotation is declared locally, false otherwise
     */
    public static boolean isAnnotationDeclaredLocally(Class<? extends Annotation> annotationType, Class<?> targetType) {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(targetType, "targetType");
        return targetType.getDeclaredAnnotation(annotationType) != null;
    }

    /**
     * Equivalent-ish to Spring's AnnotationUtils.findAnnotation(clazz, annotationType).
     * <p>
     * Scans superclasses and interfaces if @Inherited applies OR if scanHierarchy is true.
     *
     * @param targetType     the target class to scan
     * @param annotationType the annotation type to find
     * @return the annotation if found, or null otherwise
     */
    public static <A extends Annotation> A findAnnotation(Class<?> targetType, Class<A> annotationType) {
        return findAnnotation(targetType, annotationType, true);
    }

    public static <A extends Annotation> A findAnnotation(Class<?> targetType, Class<A> annotationType, boolean scanHierarchy) {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(annotationType, "annotationType");

        boolean inherited = annotationType.isAnnotationPresent(Inherited.class);

        Deque<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(targetType);

        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            A direct = current.getDeclaredAnnotation(annotationType);
            if (direct != null) {
                return direct;
            }

            A meta = findInMetaAnnotations(current, annotationType);
            if (meta != null) {
                return meta;
            }

            if (scanHierarchy) {
                Class<?> superClass = current.getSuperclass();
                if (superClass != null && (inherited || current == targetType)) {
                    queue.add(superClass);
                }

                queue.addAll(Arrays.asList(current.getInterfaces()));
            }
        }

        return null;
    }

    private static <A extends Annotation> A findInMetaAnnotations(Class<?> current, Class<A> annotationType) {
        for (Annotation ann : current.getDeclaredAnnotations()) {
            Class<? extends Annotation> annType = ann.annotationType();

            if (annType.getName().startsWith("java.lang.annotation.")) {
                continue;
            }

            A directMeta = annType.getDeclaredAnnotation(annotationType);
            if (directMeta != null) {
                return directMeta;
            }

            for (Annotation meta : annType.getDeclaredAnnotations()) {
                Class<? extends Annotation> metaType = meta.annotationType();
                if (metaType.getName().startsWith("java.lang.annotation.")) {
                    continue;
                }
                A deep = metaType.getDeclaredAnnotation(annotationType);
                if (deep != null) {
                    return deep;
                }
            }
        }
        return null;
    }
}
