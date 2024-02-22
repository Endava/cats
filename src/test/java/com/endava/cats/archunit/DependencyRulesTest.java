package com.endava.cats.archunit;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.LinterFuzzer;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;

import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;
import static com.tngtech.archunit.library.GeneralCodingRules.DEPRECATED_API_SHOULD_NOT_BE_USED;
import static com.tngtech.archunit.library.GeneralCodingRules.testClassesShouldResideInTheSamePackageAsImplementation;

@QuarkusTest
@AnalyzeClasses(packages = "com.endava.cats")
public class DependencyRulesTest {

    @ArchTest
    static final ArchRule noInterfaceNamingForInterfaces =
            noClasses()
                    .that()
                    .areInterfaces()
                    .should()
                    .haveNameMatching(".*Interface");

    @ArchTest
    static final ArchRule interfacesMustNotBePlacedInImplementationPackages =
            noClasses()
                    .that()
                    .resideInAPackage("..impl..")
                    .should()
                    .beInterfaces();

    @ArchTest
    static ArchRule fieldsFuzzersShouldBeSuffixed =
            classes()
                    .that()
                    .resideInAPackage("..fields..")
                    .and()
                    .areAnnotatedWith(FieldFuzzer.class)
                    .should()
                    .haveSimpleNameEndingWith("Fuzzer");

    @ArchTest
    static ArchRule headersFuzzersShouldBeSuffixed =
            classes()
                    .that()
                    .resideInAPackage("..headers..")
                    .and()
                    .areAnnotatedWith(HeaderFuzzer.class)
                    .should()
                    .haveSimpleNameEndingWith("HeadersFuzzer");

    @ArchTest
    static ArchRule httpFuzzersShouldBeSuffixed =
            classes()
                    .that()
                    .resideInAPackage("..http..")
                    .and()
                    .areAnnotatedWith(HttpFuzzer.class)
                    .should()
                    .haveSimpleNameEndingWith("Fuzzer");

    @ArchTest
    static ArchRule lintersShouldBeSuffixed =
            classes()
                    .that()
                    .resideInAPackage("..contract..")
                    .and()
                    .areAnnotatedWith(LinterFuzzer.class)
                    .should()
                    .haveSimpleNameEndingWith("LinterFuzzer");

    @ArchTest
    static ArchRule dontRelyOnUpperPackages = NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;

    @ArchTest
    static ArchRule dontUseDeprecatedApis = DEPRECATED_API_SHOULD_NOT_BE_USED;

    @ArchTest
    static ArchRule testClassesInSamePackage = testClassesShouldResideInTheSamePackageAsImplementation();


    @ArchTest
    static final ArchRule noClassShouldDependOnModelUtils =
            noClasses().that()
                    .resideInAPackage("..")
                    .and()
                    .areNotAssignableFrom("com.endava.cats.util.CatsModelUtils")
                    .should()
                    .accessClassesThat()
                    .resideInAPackage("org.openapitools.codegen.utils");

    @ArchTest
    static final ArchRule noClassesShouldUseConcreteSchemas =
            noClasses().that()
                    .resideInAPackage("..")
                    .and()
                    .haveSimpleNameNotEndingWith("Test")
                    .and()
                    .areNotAssignableFrom("com.endava.cats.util.CatsModelUtils")
                    .and()
                    .areNotAssignableFrom("com.endava.cats.ReflectionConfig")
                    .should()
                    .dependOnClassesThat(new DescribedPredicate<>("not using extensions of Schema") {
                        @Override
                        public boolean test(JavaClass javaClass) {
                            return !javaClass.getSimpleName().equalsIgnoreCase("Schema") &&
                                    javaClass.isAssignableTo(Schema.class);
                        }
                    });

    @ArchTest
    static final ArchRule allUtilClassesAbstract =
            classes().that().resideInAPackage("com.endava.cats.util")
                    .and()
                    .haveSimpleNameContaining("Util")
                    .and()
                    .haveSimpleNameNotEndingWith("Test")
                    .should()
                    .haveModifier(JavaModifier.ABSTRACT);

    @ArchTest
    static final ArchRule utilClassesHaveOnlyStaticMethods =
            classes().that().resideInAPackage("com.endava.cats.util")
                    .and()
                    .haveSimpleNameContaining("Util")
                    .and()
                    .haveSimpleNameNotEndingWith("Test")
                    .should(have(DescribedPredicate.describe("only have static methods",
                            javaClass -> javaClass.getMethods().stream().filter(method -> method.getModifiers().contains(JavaModifier.STATIC)).count() == javaClass.getMethods().size())));

}
