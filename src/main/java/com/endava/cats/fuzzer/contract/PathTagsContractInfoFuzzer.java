package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.util.CollectionUtils;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ContractInfoFuzzer
@Singleton
public class PathTagsContractInfoFuzzer extends BaseContractInfoFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public PathTagsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path contains the [tags] element for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "[tags] element must be present and match the ones defined at the top level");

        List<String> topLevelTagNames = Optional.ofNullable(data.getOpenApi().getTags()).orElse(Collections.emptyList()).stream()
                .map(Tag::getName).toList();

        List<String> matching = Optional.ofNullable(data.getTags()).orElse(Collections.emptyList())
                .stream().filter(topLevelTagNames::contains).toList();

        if (CollectionUtils.isEmpty(data.getTags())) {
            testCaseListener.reportResultError(log, data, "No tag element", "The current path does not contain any [tags] element");
        } else if (matching.size() == data.getTags().size()) {
            testCaseListener.reportResultInfo(log, data, "The current path's [tags] are correctly defined at the top level [tags] element");
        } else {
            List<String> missing = new ArrayList<>(data.getTags());
            missing.removeAll(matching);
            testCaseListener.reportResultError(log, data, "Tag not present in top level tags element", "The following [tags] are not present in the top level [tags] element: {}", missing);
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
