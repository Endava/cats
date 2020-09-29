package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class BaseBoundaryFieldFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private BaseBoundaryFieldFuzzer myBaseBoundaryFuzzer;

    @Test
    void givenABaseBoundaryFuzzerWithDefinedBoundary_whenGettingTheFuzzingStrategy_thenTheReplaceStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesFuzzer(serviceCaller, testCaseListener, catsUtil);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field");
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.replace().name());
    }


    @Test
    void givenABaseBoundaryFuzzerWithNoDefinedBoundary_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithoutBoundariesFuzzer(serviceCaller, testCaseListener, catsUtil);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field");
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData()).startsWith("No LEFT or RIGHT boundary");
    }

    @Test
    void givenABaseBoundaryFuzzerWithNoDefinedBoundaryAndIntegerSchema_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(serviceCaller, testCaseListener, catsUtil);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field");
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData()).startsWith("Data type not matching [IntegerSchema]");
    }

    @Test
    void givenABaseBoundaryFuzzerAndAFieldWithNoSchema_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(serviceCaller, testCaseListener, catsUtil);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(new HashMap<>());

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field");
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData()).startsWith("Data type not matching");
    }

    private FuzzingData getMockFuzzingData() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        return data;
    }

    static class MyBaseBoundaryWithBoundariesFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithBoundariesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
        }

        @Override
        protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
            return Collections.singletonList(StringSchema.class);
        }

        @Override
        protected String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return true;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }

    static class MyBaseBoundaryWithoutBoundariesFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithoutBoundariesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
        }

        @Override
        protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
            return Collections.singletonList(StringSchema.class);
        }

        @Override
        protected String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return false;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }

    static class MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
            super(sc, lr, cu);
        }

        @Override
        protected List<Class<? extends Schema>> getSchemasThatTheFuzzerWillApplyTo() {
            return Collections.singletonList(IntegerSchema.class);
        }

        @Override
        protected String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        protected boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return true;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }
}
