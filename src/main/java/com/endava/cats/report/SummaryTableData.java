package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Compact, script-safe HTML_JS data: test rows followed by shared problem labels and anchors. */
public final class SummaryTableData {
    private static final Gson SCRIPT_JSON = new Gson();

    private SummaryTableData() {
    }

    /**
     * Encodes rows as [id, fuzzer, method, path, scenario, result, reason, switched, problemId].
     * Gson's HTML escaping keeps embedded closing script tags inert until JSON.parse reads the data.
     *
     * @param page the complete tests and problem cards
     * @return a JSON pair of all rows and the shared problem label/anchor index
     */
    public static String of(ProblemPage page) {
        List<List<Object>> rows = page.testRows().stream().map(ProblemPage.TestRow::tc).map(SummaryTableData::row).toList();
        Map<String, List<String>> labels = page.cards().stream().collect(Collectors.toMap(ProblemCard::id,
                card -> List.of(ProblemPage.label(card), card.anchor()), (first, _) -> first, LinkedHashMap::new));
        return SCRIPT_JSON.toJson(List.of(rows, labels));
    }

    private static List<Object> row(CatsTestCaseSummary test) {
        return List.of(text(test.getId()), text(test.getFuzzer()), text(test.getHttpMethod()), text(test.getPath()),
                text(test.getScenario()), text(test.getResult()), text(test.getResultReason()), test.isSwitchedResult(), text(test.getProblemId()));
    }

    private static String text(String value) {
        return StringUtils.defaultString(value);
    }
}
