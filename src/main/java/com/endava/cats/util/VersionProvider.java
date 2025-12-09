package com.endava.cats.util;

import picocli.CommandLine;

import java.util.Properties;

/**
 * Provides information about application version to PicoCli.
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    private static final String LOGO = """
            # # # # # # # # # # # # # # # # # # # # # # # # # #
            #             _____   ___ _____ _____             #
            #            /  __ \\ / _ \\_   _/  ___|            #
            #            | /  \\// /_\\ \\| | \\ `--.             #
            #            | |    |  _  || |  `--. \\            #
            #            | \\__/\\| | | || | /\\__/ /            #
            #             \\____/\\_| |_/\\_/ \\____/             #
            #           .. ...    -.-. --- --- .-..           #
            #                                                 #
            # # # # # # # # # # # # # # # # # # # # # # # # # #
            """;

    private static final String DESCRIPTION = """
            
            @|bold CATS|@ - @|green C|@ontract @|green A|@PI @|green T|@esting and @|green S|@ecurity
            
            @|bold,underline OpenAPI fuzzer and negative testing tool for REST APIs.|@
            
            CATS automatically generates and runs thousands of test cases
            to find bugs, security vulnerabilities, and contract violations
            in your API without writing a single test.
            
            @|yellow GitHub:|@  https://github.com/Endava/cats
            @|yellow Docs:|@    https://endava.github.io/cats/
            """;

    @Override
    public String[] getVersion() throws Exception {
        Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("version.properties"));
        String version = properties.getProperty("app.version");
        String buildTime = properties.getProperty("app.buildTime");

        return new String[]{
                LOGO,
                "@|bold,green " + properties.getProperty("app.name") + " version " + version + "|@",
                "@|faint Built on: " + buildTime + "|@",
                DESCRIPTION
        };
    }
}
