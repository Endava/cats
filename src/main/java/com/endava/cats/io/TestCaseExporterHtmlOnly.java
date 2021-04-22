package com.endava.cats.io;

import com.endava.cats.model.report.CatsTestReport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "reportFormat", havingValue = "htmlOnly")
public class TestCaseExporterHtmlOnly extends TestCaseExporter {

    @Override
    public String[] getSpecificHelperFiles() {
        return new String[]{"cats.png", "styles.css"};
    }

    @Override
    public Map<String, Object> getSpecificContext(CatsTestReport report) {
        return Collections.emptyMap();
    }

}
