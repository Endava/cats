package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Fuzzes search, query, and filter expression fields with malformed regex, unbalanced query-parser syntax,
 * and trailing SQL LIKE escape sequences to detect unhandled parser exceptions across backend runtimes and databases.
 */
@Singleton
@FieldFuzzer
public class SearchAndFilterExpressionFieldsFuzzer implements Fuzzer {
    private static final Set<String> SEARCH_FILTER_FIELD_NAMES = Set.of(
            "search", "q", "query", "filter", "keyword", "keywords",
            "pattern", "regex", "match", "term", "where", "like", "contains"
    );
    private static final List<Object> MALFORMED_SEARCH_EXPRESSIONS = List.of(
            "[unclosed(",
            "AND OR NOT [0 TO *}",
            "%\\_%\\"
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(SearchAndFilterExpressionFieldsFuzzer.class);
    private final FieldsIteratorExecutor catsExecutor;

    /**
     * Creates a new SearchAndFilterExpressionFieldsFuzzer instance.
     *
     * @param catsExecutor the fields iterator executor
     */
    public SearchAndFilterExpressionFieldsFuzzer(FieldsIteratorExecutor catsExecutor) {
        this.catsExecutor = catsExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Send malformed regex, query-parser, and wildcard escape expressions in search/filter fields to verify parser error handling.")
                        .fuzzingData(data)
                        .logger(logger)
                        .fuzzer(this)
                        .fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                        .schemaFilter(schema -> CatsModelUtils.isStringSchema(schema)
                                || (CatsModelUtils.isArraySchema(schema) && CatsModelUtils.isStringSchema(schema.getItems())))
                        .fieldFilter(SearchAndFilterExpressionFieldsFuzzer::isSearchOrFilterField)
                        .fuzzValueProducer((_, _) -> MALFORMED_SEARCH_EXPRESSIONS)
                        .skipMessage("Field is not a search, query, or filter string field.")
                        .build()
        );
    }

    static boolean isSearchOrFilterField(String fieldName) {
        return SEARCH_FILTER_FIELD_NAMES.contains(WordUtils.normalizeLeaf(fieldName));
    }

    @Override
    public String description() {
        return "iterates through search, query, and filter fields and sends malformed regex, Lucene, and wildcard escape expressions to verify parser error handling";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
