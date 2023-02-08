package com.endava.cats;

import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import io.swagger.v3.oas.models.media.StringSchema;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Setup {

    public static FuzzingData setupSimpleFuzzingData() {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        return FuzzingData.builder().headers(Collections.singleton(CatsHeader.builder().name("header").value("value").build())).
                responses(responses).reqSchema(new StringSchema()).requestContentTypes(List.of("application/json")).build();
    }
}
