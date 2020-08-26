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

    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final CatsUtil catsUtil;
    @Value("${urlParams:empty}")
    private String params;
    private List<String> urlParamsList = new ArrayList<>();
    @Value("${headers:empty}")
    private String headersFile;

    @Autowired
    public CatsParams(CatsUtil cu) {
        this.catsUtil = cu;
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
}
