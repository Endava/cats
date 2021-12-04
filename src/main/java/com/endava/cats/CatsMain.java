package com.endava.cats;

import com.endava.cats.command.CatsCommand;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

import javax.inject.Inject;

/**
 * We need to print the build version and build time
 */

@QuarkusMain
public class CatsMain implements QuarkusApplication {

    @Inject
    CatsCommand catsCommand;

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run(String... args) {
        return new CommandLine(catsCommand, factory)
                .setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    }
}