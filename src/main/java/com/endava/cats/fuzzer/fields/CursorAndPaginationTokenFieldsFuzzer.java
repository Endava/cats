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
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

/**
 * Fuzzes cursor and continuation pagination token fields with valid Base64 payloads containing
 * unexpected inner shapes (empty JSON object, JSON null, non-numeric delimiter segments) and
 * malformed Base64 padding to detect unhandled cursor-decoder exceptions across backend runtimes.
 */
@Singleton
@FieldFuzzer
public class CursorAndPaginationTokenFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CursorAndPaginationTokenFieldsFuzzer.class);
    private final FieldsIteratorExecutor catsExecutor;

    /**
     * Creates a new CursorAndPaginationTokenFieldsFuzzer instance.
     *
     * @param catsExecutor the fields iterator executor
     */
    public CursorAndPaginationTokenFieldsFuzzer(FieldsIteratorExecutor catsExecutor) {
        this.catsExecutor = catsExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        catsExecutor.execute(
                FieldsIteratorExecutorContext.builder()
                        .scenario("Send Base64-encoded empty/null JSON, corrupted delimiter segments, and malformed padding in cursor/pagination token fields.")
                        .fuzzingData(data)
                        .logger(logger)
                        .fuzzer(this)
                        .fuzzingStrategy(FuzzingStrategy.replace())
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                        .schemaFilter(CatsModelUtils::isStringSchema)
                        .fieldFilter(CursorAndPaginationTokenFieldsFuzzer::isCursorField)
                        .fuzzValueProducer((_, _) -> Base64DecoderInputs.cursorValues())
                        .skipMessage("Field is not a cursor or pagination token string field.")
                        .build()
        );
    }

    static boolean isCursorField(String fieldName) {
        return Base64DecoderInputs.isCursorField(fieldName);
    }

    @Override
    public String description() {
        return "iterates through cursor and pagination token fields and sends Base64 payloads with unexpected inner structures to verify cursor decoder robustness";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
