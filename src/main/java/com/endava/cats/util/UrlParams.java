package com.endava.cats.util;

import com.endava.cats.CatsMain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UrlParams {

    @Value("${urlParams:empty}")
    private String params;
    private List<String> urlParamsList = new ArrayList<>();

    @PostConstruct
    public void loadURLParams() {
        if (CatsMain.EMPTY.equalsIgnoreCase(params)) {
            log.info("No URL parameters supplied!");
        } else {
            urlParamsList = Arrays.stream(params.split(",")).map(String::trim).collect(Collectors.toList());
            log.info("URL parameters: {}", urlParamsList);
        }
    }

    public List<String> getUrlParamsList() {
        return this.urlParamsList;
    }
}
