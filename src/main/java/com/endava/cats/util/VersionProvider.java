package com.endava.cats.util;

import picocli.CommandLine;

import java.util.Properties;

/**
 * Provides information about application version to PicoCli.
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("version.properties"));
        return new String[]{"%n@|green " + properties.getProperty("app.name") + " version " +
                properties.getProperty("app.version") + "|@"};
    }
}
