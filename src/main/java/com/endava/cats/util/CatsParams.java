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

    @Value("${urlParams:empty}")
    private String params;
    private List<String> urlParamsList = new ArrayList<>();


    @Value("${headers:empty}")
    private String headersFile;
    private Map<String, Map<String, String>> headers = new HashMap<>();

    private CatsUtil catsUtil;

    @Autowired
    public CatsParams(CatsUtil cu) {
        this.catsUtil = cu;
    }

    @PostConstruct
    public void loadURLParams() {
        if (CatsMain.EMPTY.equalsIgnoreCase(params)) {
            log.info("No URL parameters supplied!");
        } else {
            urlParamsList = Arrays.stream(params.split(",")).map(String::trim).collect(Collectors.toList());
            log.info("URL parameters: {}", urlParamsList);
        }
    }

    @PostConstruct
    public void loadHeaders() throws IOException {
        if (CatsMain.EMPTY.equalsIgnoreCase(headersFile)) {
            log.info("No headers file was supplied! No additional header will be added!");
        } else {
            catsUtil.mapObjsToString(headersFile, headers);
        }
    }

    public List<String> getUrlParamsList() {
        return this.urlParamsList;
    }

    public Map<String, Map<String, String>> getHeaders() {
        return this.headers;
    }
}
