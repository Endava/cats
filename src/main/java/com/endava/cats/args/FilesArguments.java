package com.endava.cats.args;

import com.endava.cats.util.CatsUtil;
import com.google.common.collect.Maps;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FilesArguments {
    private static final String ALL = "all";
    private static final String EMPTY = "empty";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final Map<String, Map<String, String>> refData = new HashMap<>();
    private final CatsUtil catsUtil;

    @Getter
    private final List<CatsArg> args = new ArrayList<>();

    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();
    private Map<String, Map<String, Object>> securityFuzzerDetails = new HashMap<>();
    private List<String> urlParamsList = new ArrayList<>();

    @Value("${urlParams:empty}")
    @Getter
    private String params;
    @Value("${headers:empty}")
    @Getter
    private String headersFile;
    @Value("${refData:empty}")
    @Getter
    private String refDataFile;
    @Value("${customFuzzerFile:empty}")
    @Getter
    private String customFuzzerFile;
    @Value("${securityFuzzerFile:empty}")
    @Getter
    private String securityFuzzerFile;

    @Value("${arg.files.urlParams.help:help}")
    private String paramsHelp;
    @Value("${arg.files.headers.help:help}")
    private String headersFileHelp;
    @Value("${arg.files.refData.help:help}")
    private String refDataFileHelp;
    @Value("${arg.files.customFuzzerFile.help:help}")
    private String customFuzzerFileHelp;
    @Value("${arg.files.securityFuzzerFile.help:help}")
    private String securityFuzzerFileHelp;

    @Autowired
    public FilesArguments(CatsUtil cu) {
        this.catsUtil = cu;
    }

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("urlParams").value(params).help(paramsHelp).build());
        args.add(CatsArg.builder().name("headers").value(headersFile).help(headersFileHelp).build());
        args.add(CatsArg.builder().name("refData").value(refDataFile).help(refDataFileHelp).build());
        args.add(CatsArg.builder().name("customFuzzerFile").value(customFuzzerFile).help(customFuzzerFileHelp).build());
        args.add(CatsArg.builder().name("securityFuzzerFile").value(securityFuzzerFile).help(securityFuzzerFileHelp).build());
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
        if (EMPTY.equalsIgnoreCase(securityFuzzerFile)) {
            log.info("No security custom Fuzzer file. SecurityFuzzer will be skipped!");
        } else {
            securityFuzzerDetails = catsUtil.parseYaml(securityFuzzerFile);
        }
    }

    public void loadCustomFuzzerFile() throws IOException {
        if (EMPTY.equalsIgnoreCase(customFuzzerFile)) {
            log.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
        } else {
            customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile);
        }
    }

    public void loadRefData() throws IOException {
        if (EMPTY.equalsIgnoreCase(refDataFile)) {
            log.info("No reference data file was supplied! Payloads supplied by Fuzzers will remain unchanged!");
        } else {
            catsUtil.loadFileToMap(refDataFile, refData);
            log.info("Reference data file loaded successfully: {}", refData);
        }
    }

    public void loadURLParams() throws IOException {
        try {
            if (EMPTY.equalsIgnoreCase(params)) {
                log.info("No URL parameters supplied!");
            } else {
                urlParamsList = Arrays.stream(params.split(",")).map(String::trim).collect(Collectors.toList());
                log.info("URL parameters: {}", urlParamsList);
            }
        } catch (Exception e) {
            throw new IOException("There was a problem parsing the urlParams argument: " + e.getMessage());
        }
    }

    public void loadHeaders() throws IOException {
        try {
            if (EMPTY.equalsIgnoreCase(headersFile)) {
                log.info("No headers file was supplied! No additional header will be added!");
            } else {
                catsUtil.loadFileToMap(headersFile, headers);
            }
        } catch (Exception e) {
            throw new IOException("There was a problem parsing the headers file: " + e.getMessage());
        }
    }


    /**
     * Returns a list of the --urlParams supplied. The list will contain strings in the {@code name:value} pairs.
     *
     * @return a name:value list of url params to be replaced when calling the service
     */
    public List<String> getUrlParamsList() {
        return this.urlParamsList;
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
                startingUrl = startingUrl.replace("{" + urlParam[0] + "}", urlParam[1]);
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
