package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class AnnotationUtilsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface TestAnnotation {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface InheritedTestAnnotation {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface MetaAnnotation {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @MetaAnnotation
    @interface AnnotatedWithMeta {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface DeepMetaAnnotation {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @DeepMetaAnnotation
    @interface MidLevelMeta {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @MidLevelMeta
    @interface TopLevelMeta {
    }

    @TestAnnotation
    static class ClassWithAnnotation {
    }

    static class ClassWithoutAnnotation {
    }

    @InheritedTestAnnotation
    static class ParentWithInheritedAnnotation {
    }

    static class ChildOfInheritedParent extends ParentWithInheritedAnnotation {
    }

    @TestAnnotation
    static class ParentWithNonInheritedAnnotation {
    }

    static class ChildOfNonInheritedParent extends ParentWithNonInheritedAnnotation {
    }

    @AnnotatedWithMeta
    static class ClassWithMetaAnnotation {
    }

    @TopLevelMeta
    static class ClassWithDeepMetaAnnotation {
    }


    @TestAnnotation
    interface AnnotatedInterfaceExample {
    }

    static class ClassImplementingAnnotatedInterfaceExample implements AnnotatedInterfaceExample {
    }

    static class MultiLevelChild extends ChildOfInheritedParent {
    }

    @Nested
    @DisplayName("isAnnotationDeclaredLocally Tests")
    class IsAnnotationDeclaredLocallyTests {

        @Test
        @DisplayName("Should return true when annotation is declared locally")
        void shouldReturnTrueWhenAnnotationDeclaredLocally() {
            boolean result = AnnotationUtils.isAnnotationDeclaredLocally(TestAnnotation.class, ClassWithAnnotation.class);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when annotation is not declared locally")
        void shouldReturnFalseWhenAnnotationNotDeclaredLocally() {
            boolean result = AnnotationUtils.isAnnotationDeclaredLocally(TestAnnotation.class, ClassWithoutAnnotation.class);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for inherited annotation on child class")
        void shouldReturnFalseForInheritedAnnotationOnChild() {
            boolean result = AnnotationUtils.isAnnotationDeclaredLocally(InheritedTestAnnotation.class, ChildOfInheritedParent.class);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true for inherited annotation on parent class")
        void shouldReturnTrueForInheritedAnnotationOnParent() {
            boolean result = AnnotationUtils.isAnnotationDeclaredLocally(InheritedTestAnnotation.class, ParentWithInheritedAnnotation.class);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should throw NullPointerException when annotationType is null")
        void shouldThrowNPEWhenAnnotationTypeIsNull() {
            assertThatThrownBy(() -> AnnotationUtils.isAnnotationDeclaredLocally(null, ClassWithAnnotation.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("annotationType");
        }

        @Test
        @DisplayName("Should throw NullPointerException when targetType is null")
        void shouldThrowNPEWhenTargetTypeIsNull() {
            assertThatThrownBy(() -> AnnotationUtils.isAnnotationDeclaredLocally(TestAnnotation.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("targetType");
        }
    }

    @Nested
    @DisplayName("findAnnotation Tests")
    class FindAnnotationTests {

        @Test
        @DisplayName("Should find annotation declared locally")
        void shouldFindAnnotationDeclaredLocally() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ClassWithAnnotation.class, TestAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return null when annotation not found")
        void shouldReturnNullWhenAnnotationNotFound() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ClassWithoutAnnotation.class, TestAnnotation.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should find inherited annotation on child class")
        void shouldFindInheritedAnnotationOnChild() {
            InheritedTestAnnotation result = AnnotationUtils.findAnnotation(ChildOfInheritedParent.class, InheritedTestAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find non-inherited annotation on parent when scanning hierarchy")
        void shouldFindNonInheritedAnnotationOnParentWhenScanningHierarchy() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ChildOfNonInheritedParent.class, TestAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should not find non-inherited annotation when not scanning hierarchy")
        void shouldNotFindNonInheritedAnnotationWhenNotScanningHierarchy() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ChildOfNonInheritedParent.class, TestAnnotation.class, false);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should find meta-annotation")
        void shouldFindMetaAnnotation() {
            MetaAnnotation result = AnnotationUtils.findAnnotation(ClassWithMetaAnnotation.class, MetaAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find deep meta-annotation")
        void shouldFindDeepMetaAnnotation() {
            DeepMetaAnnotation result = AnnotationUtils.findAnnotation(ClassWithDeepMetaAnnotation.class, DeepMetaAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find annotation on interface")
        void shouldFindAnnotationOnInterface() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ClassImplementingAnnotatedInterfaceExample.class, TestAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should find inherited annotation on multi-level child")
        void shouldFindInheritedAnnotationOnMultiLevelChild() {
            InheritedTestAnnotation result = AnnotationUtils.findAnnotation(MultiLevelChild.class, InheritedTestAnnotation.class);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should throw NullPointerException when targetType is null")
        void shouldThrowNPEWhenTargetTypeIsNull() {
            assertThatThrownBy(() -> AnnotationUtils.findAnnotation(null, TestAnnotation.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("targetType");
        }

        @Test
        @DisplayName("Should throw NullPointerException when annotationType is null")
        void shouldThrowNPEWhenAnnotationTypeIsNull() {
            assertThatThrownBy(() -> AnnotationUtils.findAnnotation(ClassWithAnnotation.class, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("annotationType");
        }

        @Test
        @DisplayName("Should handle class with no superclass")
        void shouldHandleClassWithNoSuperclass() {
            TestAnnotation result = AnnotationUtils.findAnnotation(Object.class, TestAnnotation.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle circular reference in hierarchy")
        void shouldHandleCircularReferenceInHierarchy() {
            TestAnnotation result = AnnotationUtils.findAnnotation(ChildOfInheritedParent.class, TestAnnotation.class);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should not scan hierarchy when scanHierarchy is false")
        void shouldNotScanHierarchyWhenScanHierarchyIsFalse() {
            InheritedTestAnnotation result = AnnotationUtils.findAnnotation(ChildOfInheritedParent.class, InheritedTestAnnotation.class, false);

            assertThat(result).isNull();
        }
    }
}
