package com.endava.cats.command.model;

import jakarta.inject.Singleton;
import picocli.CommandLine;

import java.io.File;

@Singleton
public class ConfigOptions {
    @CommandLine.Option(names = "--configFile", description = "Path to config file")
    public File configFile;
}
