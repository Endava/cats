package com.endava.cats.archunit;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.quarkus.test.junit.QuarkusTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
