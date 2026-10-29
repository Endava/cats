package com.endava.cats.command.model;

import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;

@Singleton
public class ConfigOptions {
    @CommandLine.Option(names = "--configFile", description = "Path to config file")
    public File configFile;

    @Getter
    @Setter
    private Path resolvedConfigFile;
}
