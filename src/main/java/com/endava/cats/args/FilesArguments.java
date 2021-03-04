package com.endava.cats.args;

import com.endava.cats.CatsMain;
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

    @Autowired
    public FilesArguments(CatsUtil cu) {
        this.catsUtil = cu;
    }

    @PostConstruct
    public void init() {
        args.add(CatsArg.builder().name("urlParams").value(params).help("A comma separated list of 'name:value' pairs of parameters to be replaced inside the URLs").build());
        args.add(CatsArg.builder().name("headers").value(headersFile).help("FILE specifies custom headers that will be passed along with request. This can be used to pass oauth or JWT tokens for authentication purposed for example").build());
        args.add(CatsArg.builder().name("refData").value(refDataFile).help("FILE specifies the file with fields that must have a fixed value in order for requests to succeed").build());
        args.add(CatsArg.builder().name("customFuzzerFile").value(customFuzzerFile).help("A file used by the `CustomFuzzer` that will be used to create user-supplied payloads").build());
        args.add(CatsArg.builder().name("securityFuzzerFile").value(securityFuzzerFile).help("A file used by the `SecurityFuzzer` that will be used to inject special strings in order to exploit possible vulnerabilities").build());
    }

    public void loadConfig() throws IOException {
        loadSecurityFuzzerFile();
        loadCustomFuzzerFile();
        loadRefData();
        loadURLParams();
        loadHeaders();
    }

    public void loadSecurityFuzzerFile() {
        try {
            if (EMPTY.equalsIgnoreCase(securityFuzzerFile)) {
                log.info("No security custom Fuzzer file. SecurityFuzzer will be skipped!");
            } else {
                securityFuzzerDetails = catsUtil.parseYaml(securityFuzzerFile);
            }
        } catch (Exception e) {
            log.error("Error processing securityFuzzerFile!", e);
        }
    }

    public void loadCustomFuzzerFile() {
        try {
            if (EMPTY.equalsIgnoreCase(customFuzzerFile)) {
                log.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
            } else {
                customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile);
            }
        } catch (Exception e) {
            log.error("Error processing customFuzzerFile!", e);
        }
    }

    public void loadRefData() throws IOException {
        try {
            if (EMPTY.equalsIgnoreCase(refDataFile)) {
                log.info("No reference data file was supplied! Payloads supplied by Fuzzers will remain unchanged!");
            } else {
                catsUtil.mapObjsToString(refDataFile, refData);
                log.info("Reference data file loaded successfully: {}", refData);
            }
        } catch (Exception e) {
            throw new IOException("There was a problem parsing the refData file: " + e.getMessage());
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
                catsUtil.mapObjsToString(headersFile, headers);
            }
        } catch (Exception e) {
            throw new IOException("There was a problem parsing the headers file: " + e.getMessage());
        }
    }

    public List<String> getUrlParamsList() {
        return this.urlParamsList;
    }

    public Map<String, Map<String, String>> getHeaders() {
        return this.headers;
    }

    public Map<String, String> getRefData(String currentPath) {
        Map<String, String> currentPathRefData = Optional.ofNullable(this.refData.get(currentPath)).orElse(Maps.newHashMap());
        Map<String, String> allValues = Optional.ofNullable(this.refData.get(ALL)).orElse(Collections.emptyMap());

        currentPathRefData.putAll(allValues);
        return currentPathRefData;
    }

    public Map<String, Map<String, Object>> getCustomFuzzerDetails() {
        return this.customFuzzerDetails;
    }

    public Map<String, Map<String, Object>> getSecurityFuzzerDetails() {
        return this.securityFuzzerDetails;
    }

}
