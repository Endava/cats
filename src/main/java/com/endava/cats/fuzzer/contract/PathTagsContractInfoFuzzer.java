package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ContractInfoFuzzer
@Component
@Slf4j
public class PathTagsContractInfoFuzzer extends BaseContractInfoFuzzer {

    @Autowired
    public PathTagsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path contains the tags element");
        testCaseListener.addExpectedResult(log, "[tags] element must be present and match the ones defined at the top level");

        List<String> topLevelTagNames = Optional.ofNullable(data.getOpenApi().getTags()).orElse(Collections.emptyList()).stream()
                .map(Tag::getName).collect(Collectors.toList());

        List<String> matching = Optional.ofNullable(data.getTags()).orElse(Collections.emptyList())
                .stream().filter(topLevelTagNames::contains).collect(Collectors.toList());

        if (CollectionUtils.isEmpty(data.getTags())) {
            testCaseListener.reportError(log, "The current path does not contain any [tags] element");
        } else if (matching.size() == data.getTags().size()) {
            testCaseListener.reportInfo(log, "The current path's [tags] are correctly defined in the top level [tags] element");
        } else {
            List<String> missing = new ArrayList<>(data.getTags());
            missing.removeAll(matching);
            testCaseListener.reportError(log, "The following [tags] are not present in the top level [tags] element: {}", missing);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI paths contain tags elements and checks if the tags elements match the ones declared at the top level";
    }
}
