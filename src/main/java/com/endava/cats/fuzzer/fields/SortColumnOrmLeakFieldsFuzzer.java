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
 * Fuzzes sort/order-by fields and query parameters with invalid or nested ORM property paths
 * to detect unhandled ORM property reference exceptions (500 Internal Server Error).
 */
@Singleton
@FieldFuzzer
public class SortColumnOrmLeakFieldsFuzzer implements Fuzzer {
    private static final Set<String> SORT_FIELD_NAMES = Set.of(
            "sort", "sortby", "sortcolumn", "sortbycolumn", "sortfield", "orderby", "ordercolumn", "orderfield"
    );
    private static final List<Object> INVALID_SORT_EXPRESSIONS = List.of(
            "__cats_non_existent_column__",
            "id.nonExistentNestedProperty",
            "id ASC, __cats_invalid_column__"
    );

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(SortColumnOrmLeakFieldsFuzzer.class);
    private final FieldsIteratorExecutor catsExecutor;

    /**
     * Creates a new SortColumnOrmLeakFieldsFuzzer instance.
     *
     * @param catsExecutor the fields iterator executor
     */
    public SortColumnOrmLeakFieldsFuzzer(FieldsIteratorExecutor catsExecutor) {
        this.catsExecutor = catsExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Send invalid and nested ORM property names in sort/order-by fields to verify sort column validation.")
                        .fuzzingData(data)
                        .logger(logger)
                        .fuzzer(this)
                        .fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                        .schemaFilter(schema -> CatsModelUtils.isStringSchema(schema)
                                || (CatsModelUtils.isArraySchema(schema) && CatsModelUtils.isStringSchema(schema.getItems())))
                        .fieldFilter(SortColumnOrmLeakFieldsFuzzer::isSortField)
                        .fuzzValueProducer((_, _) -> INVALID_SORT_EXPRESSIONS)
                        .skipMessage("Field is not a sort/order-by string field.")
                        .build()
        );
    }

    static boolean isSortField(String fieldName) {
        return SORT_FIELD_NAMES.contains(WordUtils.normalizeLeaf(fieldName));
    }

    @Override
    public String description() {
        return "iterates through sort/order-by fields and sends invalid or nested ORM property names to verify sort column validation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
