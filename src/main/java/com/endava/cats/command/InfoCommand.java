package com.endava.cats.command;

import com.endava.cats.json.JsonUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.quarkus.bootstrap.graal.ImageInfo;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

@CommandLine.Command(
        name = "info",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "List Fuzzers, OpenAPI paths and FieldFuzzing strategies",
        versionProvider = VersionProvider.class)
@Unremovable
public class InfoCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output to console in JSON format.")
    private boolean json;

    @Getter
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;
    @ConfigProperty(name = "app.timestamp", defaultValue = "1-1-1")
    String appBuildTime;

    @Override
    public void run() {
        String imageType = ImageInfo.inImageRuntimeCode() ? "native" : "uber-jar";
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        CatsInfo catsInfo = new CatsInfo(osName, osVersion, osArch, appVersion, imageType, appBuildTime);

        if (json) {
            displayJson(catsInfo);
        } else {
            displayText(catsInfo);
        }

    }

    void displayJson(CatsInfo catsInfo) {
        logger.noFormat(JsonUtils.GSON.toJson(catsInfo));
    }

    void displayText(CatsInfo catsInfo) {
        logger.noFormat("Key           | Value");
        logger.noFormat("------------- | --------------------");
        logger.noFormat("OS Name       | " + catsInfo.osName);
        logger.noFormat("OS Version    | " + catsInfo.osVersion);
        logger.noFormat("OS Arch       | " + catsInfo.osArch);
        logger.noFormat("Binary Type   | " + catsInfo.imageType);
        logger.noFormat("Cats Version  | " + catsInfo.version);
        logger.noFormat("Cats Build    | " + catsInfo.buildTime);
    }

    public record CatsInfo(String osName, String osVersion, String osArch, String version, String imageType,
                           String buildTime) {
    }
}
