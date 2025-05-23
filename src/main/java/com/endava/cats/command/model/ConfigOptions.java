package com.endava.cats.command.model;

import picocli.CommandLine;

import java.io.File;

public class ConfigOptions {
    @CommandLine.Option(names = "--configFile", description = "Path to config file")
    public File configFile;
}
