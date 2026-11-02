package com.endava.cats.generator.format.impl;

import com.endava.cats.util.CatsFaker;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

@QuarkusTest
class DescriptionGeneratorTest {
    @Test
    void shouldGenerate() {
        DescriptionGenerator descriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(descriptionGenerator.generate(new Schema<>()).toString()).hasSizeGreaterThan(1);
    }

    @ParameterizedTest
    @CsvSource({"Description,true", "other,false"})
    void shouldApplyToFormat(String format, boolean expected) {
        DescriptionGenerator descriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(descriptionGenerator.appliesTo(format, "")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Description,true", "description,true", "other#Description,true", "requestBody,true", "other, false"})
    void shouldApplyToPropertyName(String property, boolean expected) {
        DescriptionGenerator descriptionGenerator = new DescriptionGenerator();
        Assertions.assertThat(descriptionGenerator.appliesTo("", property)).isEqualTo(expected);
    }

    @Test
    void shouldRejectOverlongCandidatesAndCombineShortCandidatesOnlyWhenAllowed() {
        DescriptionGenerator generator = new DescriptionGenerator();
        CatsFaker faker = Mockito.mock(CatsFaker.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(faker.chuckNorris().fact()).thenReturn("Short fact.");
        try (var utilities = Mockito.mockStatic(CatsUtil.class)) {
            utilities.when(CatsUtil::catsFaker).thenReturn(faker);
            Assertions.assertThat(generator.matchingFormats()).containsExactly("description");
            Assertions.assertThat(generator.generate(new Schema<>().maxLength(10))).isNull();
            Assertions.assertThat(generator.generate(new Schema<>().maxLength(11)))
                    .isEqualTo("Short fact.");
            Assertions.assertThat(generator.generate(new Schema<>().maxLength(120)))
                    .isEqualTo("Short fact. Short fact.");
        }
    }
}
