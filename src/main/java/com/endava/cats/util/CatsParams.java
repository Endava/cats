package com.endava.cats.util;

import com.endava.cats.CatsMain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CatsParams {
    private static final String ALL = "all";
    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final CatsUtil catsUtil;
    private final Map<String, Map<String, String>> refData = new HashMap<>();
    @Value("${urlParams:empty}")
    private String params;
    private List<String> urlParamsList = new ArrayList<>();
    @Value("${headers:empty}")
    private String headersFile;
    @Value("${refData:empty}")
    private String refDataFile;

    @Autowired
    public CatsParams(CatsUtil cu) {
        this.catsUtil = cu;
    }


    @PostConstruct
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

    @PostConstruct
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

    @PostConstruct
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
        Map<String, String> currentPathRefData = Optional.ofNullable(this.refData.get(currentPath)).orElse(Collections.emptyMap());
        Map<String, String> allValues = Optional.ofNullable(this.refData.get(ALL)).orElse(Collections.emptyMap());

        currentPathRefData.putAll(allValues);
        return currentPathRefData;
    }
}
