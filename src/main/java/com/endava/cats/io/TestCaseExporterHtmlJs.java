package com.endava.cats.io;

import com.endava.cats.model.report.CatsTestCaseSummary;
import com.endava.cats.model.report.CatsTestReport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "reportFormat", havingValue = "htmlJs", matchIfMissing = true)
public class TestCaseExporterHtmlJs extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"cats.png", "styles.css", "script.js", "jquery.min.js"};
    }

    @Override
    public Map<String, Object> getSpecificContext(CatsTestReport report) {
        Map<String, Object> context = new HashMap<>();
        List<String> fuzzers = report.getSummaryList().stream().map(CatsTestCaseSummary::getFuzzer).distinct().collect(Collectors.toList());
        context.put("FUZZERS", fuzzers);
        context.put("JS", true);
        return context;
    }
}
