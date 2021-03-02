package com.endava.cats.args;

import com.endava.cats.CatsMain;
import com.endava.cats.util.CatsUtil;
import com.google.common.collect.Maps;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FilesArguments {
    private static final String ALL = "all";
    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final CatsUtil catsUtil;
    private final Map<String, Map<String, String>> refData = new HashMap<>();
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();
    private Map<String, Map<String, Object>> securityFuzzerDetails = new HashMap<>();
    @Value("${urlParams:empty}")
    private String params;
    private List<String> urlParamsList = new ArrayList<>();
    @Value("${headers:empty}")
    private String headersFile;
    @Value("${refData:empty}")
    private String refDataFile;
    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;
    @Value("${securityFuzzerFile:empty}")
    private String securityFuzzerFile;

    @Autowired
    public FilesArguments(CatsUtil cu) {
        this.catsUtil = cu;
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
            if (CatsMain.EMPTY.equalsIgnoreCase(securityFuzzerFile)) {
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
            if (CatsMain.EMPTY.equalsIgnoreCase(customFuzzerFile)) {
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
            if (CatsMain.EMPTY.equalsIgnoreCase(refDataFile)) {
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
            if (CatsMain.EMPTY.equalsIgnoreCase(params)) {
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
            if (CatsMain.EMPTY.equalsIgnoreCase(headersFile)) {
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
