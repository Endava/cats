package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import com.google.common.collect.Maps;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FilesArguments {
    private static final String ALL = "all";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final Map<String, Map<String, String>> refData = new HashMap<>();
    private final CatsUtil catsUtil;

    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();
    private Map<String, Map<String, Object>> securityFuzzerDetails = new HashMap<>();

    @CommandLine.Option(names = {"--urlParams"},
            description = "A comma separated list of 'name:value' pairs of parameters to be replaced inside the URLs", split = ",")
    @Getter
    private List<String> params;

    @CommandLine.Option(names = {"--headers"},
            description = "Specifies custom headers that will be passed along with request. This can be used to pass oauth or JWT tokens for authentication purposed for example")
    @Getter
    private File headersFile;

    @CommandLine.Option(names = {"--refData"},
            description = "Specifies the file with fields that must have a fixed value in order for requests to succeed")
    @Getter
    private File refDataFile;

    @CommandLine.Option(names = {"--customFuzzerFile"},
            description = "Specifies the file used by the `CustomFuzzer` that will be used to create user-supplied payloads")
    @Getter
    private File customFuzzerFile;

    @CommandLine.Option(names = {"--securityFuzzerFile"},
            description = "Specifies the file used by the `SecurityFuzzer` that will be used to inject special strings in order to exploit possible vulnerabilities")
    @Getter
    private File securityFuzzerFile;

    @Autowired
    public FilesArguments(CatsUtil cu) {
        this.catsUtil = cu;
    }

    /**
     * Loads all supplied files for --securityFuzzerFile, --customFuzzerFile, --refData, --urlParams and --headers.
     *
     * @throws IOException if something happens during file processing
     */
    public void loadConfig() throws IOException {
        loadSecurityFuzzerFile();
        loadCustomFuzzerFile();
        loadRefData();
        loadURLParams();
        loadHeaders();
    }

    public void loadSecurityFuzzerFile() throws IOException {
        if (securityFuzzerFile == null) {
            log.info("No security custom Fuzzer file. SecurityFuzzer will be skipped!");
        } else {
            securityFuzzerDetails = catsUtil.parseYaml(securityFuzzerFile.getAbsolutePath());
        }
    }

    public void loadCustomFuzzerFile() throws IOException {
        if (customFuzzerFile == null) {
            log.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
        } else {
            customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile.getAbsolutePath());
        }
    }

    public void loadRefData() throws IOException {
        if (refDataFile == null) {
            log.info("No reference data file was supplied! Payloads supplied by Fuzzers will remain unchanged!");
        } else {
            catsUtil.loadFileToMap(refDataFile.getAbsolutePath(), refData);
            log.info("Reference data file loaded successfully: {}", refData);
        }
    }

    public void loadURLParams() {
        if (params == null) {
            log.info("No URL parameters supplied!");
        } else {
            log.info("URL parameters: {}", params);
        }
    }

    public void loadHeaders() throws IOException {
        if (headersFile == null) {
            log.info("No headers file was supplied! No additional header will be added!");
        } else {
            catsUtil.loadFileToMap(headersFile.getAbsolutePath(), headers);
        }
    }


    /**
     * Returns a list of the --urlParams supplied. The list will contain strings in the {@code name:value} pairs.
     *
     * @return a name:value list of url params to be replaced when calling the service
     */
    public List<String> getUrlParamsList() {
        return Optional.ofNullable(this.params).orElse(Collections.emptyList());
    }

    /**
     * Replaces the current URL parameters with the {@code --urlParams} arguments supplied.
     * The URL parameters are expected to be included in curly brackets.
     *
     * @param startingUrl the base url; http://localhost:8080/{petId}
     * @return the initial URL with the parameters replaced with --urlParams supplied values
     */
    public String replacePathWithUrlParams(String startingUrl) {
        for (String line : this.getUrlParamsList()) {
            String[] urlParam = line.split(":");
            String pathVar = "{" + urlParam[0] + "}";
            if (startingUrl.contains(pathVar)) {
                startingUrl = startingUrl.replace(pathVar, urlParam[1]);
            }
        }
        return startingUrl;
    }

    /**
     * Returns the header values supplied in the --headers argument.
     * <p>
     * The map keys are the contract paths or {@code all} if headers are applied to all paths.
     *
     * @return a Map representation of the --headers file with paths being the Map keys
     */
    public Map<String, Map<String, String>> getHeaders() {
        return this.headers;
    }

    /**
     * Returns the reference data from the --refData file supplied as argument.
     * <p>
     * It return reference data for both the given path and under the {@code all} key.
     *
     * @param currentPath the current API path
     * @return a Map with the supplied --refData
     */
    public Map<String, String> getRefData(String currentPath) {
        Map<String, String> currentPathRefData = Optional.ofNullable(this.refData.get(currentPath)).orElse(Maps.newHashMap());
        Map<String, String> allValues = Optional.ofNullable(this.refData.get(ALL)).orElse(Collections.emptyMap());

        currentPathRefData.putAll(allValues);
        return currentPathRefData;
    }

    /**
     * Returns a Map representation of the --customFuzzer. Map keys are test cases IDs.
     *
     * @return a Map representation of the --customFuzzer
     */
    public Map<String, Map<String, Object>> getCustomFuzzerDetails() {
        return this.customFuzzerDetails;
    }

    /**
     * Returns a Map representation of the --securityFuzzer. Map keys are test cases IDs.
     *
     * @return a Map representation of the --securityFuzzer
     */
    public Map<String, Map<String, Object>> getSecurityFuzzerDetails() {
        return this.securityFuzzerDetails;
    }

}
