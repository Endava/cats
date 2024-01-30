package com.endava.cats.archunit;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.LinterFuzzer;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.quarkus.test.junit.QuarkusTest;

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
}
