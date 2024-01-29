package com.endava.cats.command;

import com.endava.cats.command.model.ValidContractEntry;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.openapi.OpenApiParseResult;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import picocli.CommandLine;

import java.util.List;

/**
 * Performs strict validation for a given OpenAPI spec.
 */
@CommandLine.Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "Performs strict validation for a given OpenAPI spec",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Check if a given OpenAPI contract is valid:",
                "    cats validate -c openapi.yml"},
        versionProvider = VersionProvider.class)
@Unremovable
public class ValidateCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract", required = true)
    private String contract;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output to console in JSON format.")
    private boolean json;

    @CommandLine.Option(names = {"-d", "--detailed"},
            description = "Display details about why contract is not valid.")
    private boolean detailed;

    @Override
    public void run() {
        boolean isValid = true;
        List<String> reasons = List.of("valid");
        String version = "N/A";
        try {
            OpenApiParseResult parseResult = OpenApiUtils.readAsParseResult(contract);
            version = parseResult.getVersion().name();

            if (!parseResult.getSwaggerParseResult().getMessages().isEmpty()) {
                isValid = false;
                reasons = parseResult.getSwaggerParseResult().getMessages();
            }
        } catch (Exception e) {
            isValid = false;
            reasons = List.of(e.toString());
        }
        ValidContractEntry validContractEntry = new ValidContractEntry(isValid, version, reasons);

        printEntry(validContractEntry);
    }

    private void printEntry(ValidContractEntry validContractEntry) {
        if (json) {
            displayJson(validContractEntry);
        } else {
            displayText(validContractEntry);
        }
    }

    void displayJson(ValidContractEntry validContractEntry) {
        logger.noFormat(JsonUtils.GSON.toJson(validContractEntry));
    }

    void displayText(ValidContractEntry validContractEntry) {
        logger.noFormat("Key           | Value");
        logger.noFormat("------------- | --------------------");
        logger.noFormat("Is Valid?     | " + validContractEntry.valid());
        logger.noFormat("Version       | " + validContractEntry.version());
        if (detailed) {
            displayReasonLine(validContractEntry);
        }
    }

    void displayReasonLine(ValidContractEntry validContractEntry) {
        validContractEntry.reasons().forEach(reason -> logger.noFormat("Reason        | " + reason));
    }
}
