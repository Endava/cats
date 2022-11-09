package com.endava.cats.fuzzer.fields.base;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class BaseBoundaryFieldFuzzerTest {

    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;
    private FilesArguments filesArguments;

    private BaseBoundaryFieldFuzzer myBaseBoundaryFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        catsUtil = Mockito.mock(CatsUtil.class);
        filesArguments = Mockito.mock(FilesArguments.class);
    }

    @Test
    void givenABaseBoundaryFuzzerWithDefinedBoundary_whenGettingTheFuzzingStrategy_thenTheReplaceStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.replace().name());
    }


    @Test
    void givenABaseBoundaryFuzzerWithNoDefinedBoundary_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithoutBoundariesFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData().toString()).startsWith("No LEFT or RIGHT boundary");
    }

    @Test
    void givenABaseBoundaryFuzzerWithNoDefinedBoundaryAndIntegerSchema_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);

        FuzzingData data = getMockFuzzingData();

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData().toString()).startsWith("Data type not matching [integer]");
    }

    @Test
    void givenABaseBoundaryFuzzerAndAFieldWithNoSchema_whenGettingTheFuzzingStrategy_thenTheSkipStrategyIsBeingReturned() {
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(new HashMap<>());

        FuzzingStrategy strategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(strategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(strategy.getData().toString()).startsWith("Data type not matching");
        Assertions.assertThat(myBaseBoundaryFuzzer.typeOfDataSentToTheService()).startsWith("outside the boundary values");
    }

    @Test
    void shouldSkipWhenFormatNotRecognized() {
        FuzzingData data = getMockFuzzingData();
        StringSchema schema = new StringSchema();
        schema.setFormat("cats");
        data.getRequestPropertyTypes().put("emailAddress", schema);
        myBaseBoundaryFuzzer = new MyBaseBoundaryWithBoundariesButNoBoundaryValueFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);

        FuzzingStrategy fuzzingStrategy = myBaseBoundaryFuzzer.getFieldFuzzingStrategy(data, "emailAddress").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.skip().name());
        Assertions.assertThat(fuzzingStrategy.getData().toString()).startsWith("String format not supplied or not recognized!");
    }

    @Test
    void shouldMatchFuzzerTypeWhenSchemaAssignable() {
        myBaseBoundaryFuzzer = Mockito.mock(MyBaseBoundaryWithBoundariesFuzzer.class);
        Mockito.doCallRealMethod().when(myBaseBoundaryFuzzer).isRequestSchemaMatchingFuzzerType(Mockito.any());
        Mockito.when(myBaseBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).thenReturn(List.of("number"));

        Assertions.assertThat(myBaseBoundaryFuzzer.isRequestSchemaMatchingFuzzerType(new NumberSchema())).isTrue();
    }

    @Test
    void shouldNotMatchWhenSchemaNotAssignable() {
        myBaseBoundaryFuzzer = Mockito.mock(MyBaseBoundaryWithBoundariesFuzzer.class);
        Mockito.doCallRealMethod().when(myBaseBoundaryFuzzer).isRequestSchemaMatchingFuzzerType(Mockito.any());
        Mockito.when(myBaseBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).thenReturn(List.of("number"));

        Assertions.assertThat(myBaseBoundaryFuzzer.isRequestSchemaMatchingFuzzerType(new IntegerSchema())).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {"number,true", "string,false", "null,false"}, nullValues = "null")
    void shouldMatchFuzzerTypeWhenSchemaTypeMatchesFuzzerSchema(String schemaType, boolean matching) {
        myBaseBoundaryFuzzer = Mockito.mock(MyBaseBoundaryWithBoundariesFuzzer.class);
        Mockito.doCallRealMethod().when(myBaseBoundaryFuzzer).isRequestSchemaMatchingFuzzerType(Mockito.any());
        Mockito.when(myBaseBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo()).thenReturn(List.of("number"));
        Schema schema = new Schema();
        schema.setType(schemaType);

        Assertions.assertThat(myBaseBoundaryFuzzer.isRequestSchemaMatchingFuzzerType(schema)).isEqualTo(matching);
    }

    private FuzzingData getMockFuzzingData() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(schemaMap);
        return data;
    }

    static class MyBaseBoundaryWithBoundariesFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithBoundariesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
            return List.of("string");
        }

        @Override
        public String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return true;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }

    static class MyBaseBoundaryWithBoundariesButNoBoundaryValueFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithBoundariesButNoBoundaryValueFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
            return List.of("string");
        }

        @Override
        public String getBoundaryValue(Schema schema) {
            return null;
        }

        @Override
        public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return true;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }

    static class MyBaseBoundaryWithoutBoundariesFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithoutBoundariesFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
            return List.of("string");
        }

        @Override
        public String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return false;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }

    static class MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer extends BaseBoundaryFieldFuzzer {

        public MyBaseBoundaryWithBoundariesAndIntegerSchemaFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        public List<String> getSchemaTypesTheFuzzerWillApplyTo() {
            return List.of("integer");
        }

        @Override
        public String getBoundaryValue(Schema schema) {
            return "test";
        }

        @Override
        public boolean hasBoundaryDefined(String fuzzedField, FuzzingData data) {
            return true;
        }

        @Override
        public String description() {
            return "simple description";
        }
    }
}
