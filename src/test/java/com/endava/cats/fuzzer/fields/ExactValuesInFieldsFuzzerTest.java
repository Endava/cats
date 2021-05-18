package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.fields.base.ExactValuesInFieldsFuzzer;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Function;

@ExtendWith(SpringExtension.class)
class ExactValuesInFieldsFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private ExactValuesInFieldsFuzzer myBaseBoundaryFuzzer;

    @BeforeEach
    public void setup() {
        myBaseBoundaryFuzzer = new MyExactValueFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void shouldGetBoundaryValueForSchemaWithPattern() {
        Schema schema = new Schema();
        schema.setPattern("[0-9]+");
        schema.setMaxLength(10);
        String generated = myBaseBoundaryFuzzer.getBoundaryValue(schema);

        Assertions.assertThat(generated).matches("[0-9]+");
    }

    @Test
    void shouldGetBoundaryValueForSchemaWithNoPattern() {
        Schema schema = new Schema();
        schema.setMaxLength(10);
        String generated = myBaseBoundaryFuzzer.getBoundaryValue(schema);

        Assertions.assertThat(generated).matches(StringGenerator.ALPHANUMERIC + "+");
    }

    static class MyExactValueFuzzer extends ExactValuesInFieldsFuzzer {

        public MyExactValueFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, FilesArguments cp) {
            super(sc, lr, cu, cp);
        }

        @Override
        protected String exactValueTypeString() {
            return null;
        }

        @Override
        protected Function<Schema, Number> getExactMethod() {
            return Schema::getMaxLength;
        }
    }
}
