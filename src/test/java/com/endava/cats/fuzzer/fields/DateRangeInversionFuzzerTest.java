package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class DateRangeInversionFuzzerTest {
    private DateRangeInversionFuzzer dateRangeInversionFuzzer;
    private SimpleExecutor executor;

    @BeforeEach
    void setup() {
        executor = Mockito.mock(SimpleExecutor.class);
        dateRangeInversionFuzzer = new DateRangeInversionFuzzer(executor);
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(dateRangeInversionFuzzer.description()).isNotBlank();
        Assertions.assertThat(dateRangeInversionFuzzer.description()).containsIgnoringCase("date");
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(dateRangeInversionFuzzer).hasToString("DateRangeInversionFuzzer");
    }

    @Test
    void shouldNotRunWhenNoDateRangeFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("someField", "anotherField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(new HashMap<>());

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldRunWhenStartEndDateFieldsFound() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01\", \"endDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWhenCheckInCheckOutFieldsFound() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("checkInDate", new DateSchema());
        propertyTypes.put("checkOutDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("checkInDate", "checkOutDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"checkInDate\": \"2024-06-01\", \"checkOutDate\": \"2024-06-05\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWhenFromToDateFieldsFound() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("fromDate", new DateSchema());
        propertyTypes.put("toDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("fromDate", "toDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"fromDate\": \"2024-01-01\", \"toDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWithDateTimeFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDateTime", new DateTimeSchema());
        propertyTypes.put("endDateTime", new DateTimeSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDateTime", "endDateTime"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDateTime\": \"2024-01-01T10:00:00Z\", \"endDateTime\": \"2024-12-31T18:00:00Z\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldNotRunWhenOnlyOneFieldInPair() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("someOtherField", new StringSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "someOtherField"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01\", \"someOtherField\": \"value\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldNotRunWhenFieldValuesNotSet() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"otherField\": \"value\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldNotRunWhenDateValuesInvalid() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"not-a-date\", \"endDate\": \"also-not-a-date\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldHandleNestedDateFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("booking#startDate", new DateSchema());
        propertyTypes.put("booking#endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("booking#startDate", "booking#endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"booking\": {\"startDate\": \"2024-01-01\", \"endDate\": \"2024-12-31\"}}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleAlreadyInvertedDates() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-12-31\", \"endDate\": \"2024-01-01\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWithValidFromValidToPattern() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("validFromDate", new DateSchema());
        propertyTypes.put("validToDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("validFromDate", "validToDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"validFromDate\": \"2024-01-01\", \"validToDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.atLeastOnce()).execute(Mockito.any());
    }

    @Test
    void shouldRunWithOpenCloseDatePattern() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("openDate", new DateSchema());
        propertyTypes.put("closeDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("openDate", "closeDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"openDate\": \"2024-01-01\", \"closeDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldRunWithNullSchemaButDateFieldName() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", null);
        propertyTypes.put("endDate", null);

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01\", \"endDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldNotRunWhenSchemaNotDateAndFieldNameNotDateRelated() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new StringSchema());
        propertyTypes.put("endDate", new StringSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01\", \"endDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleAlreadyInvertedDateTimeValues() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDateTime", new DateTimeSchema());
        propertyTypes.put("endDateTime", new DateTimeSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDateTime", "endDateTime"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDateTime\": \"2024-12-31T18:00:00Z\", \"endDateTime\": \"2024-01-01T10:00:00Z\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleFieldsWithUnderscores() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("start_date", new DateSchema());
        propertyTypes.put("end_date", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("start_date", "end_date"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"start_date\": \"2024-01-01\", \"end_date\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleFieldsWithHyphens() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("start-date", new DateSchema());
        propertyTypes.put("end-date", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("start-date", "end-date"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"start-date\": \"2024-01-01\", \"end-date\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldNotRunWhenMixedDateTimeAndDateValues() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01T10:00:00Z\", \"endDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldHandleTimeFieldsWithoutDateSuffix() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startTime", new DateTimeSchema());
        propertyTypes.put("endTime", new DateTimeSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startTime", "endTime"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startTime\": \"2024-01-01T10:00:00Z\", \"endTime\": \"2024-12-31T18:00:00Z\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldNotRunWhenFieldNameDoesNotContainDateOrTime() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("checkin", new StringSchema());
        propertyTypes.put("checkout", new StringSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("checkin", "checkout"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"checkin\": \"2024-01-01\", \"checkout\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldHandleBusinessPrefixedDateFields() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("bookingStartDate", new DateSchema());
        propertyTypes.put("bookingEndDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("bookingStartDate", "bookingEndDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"bookingStartDate\": \"2024-01-01\", \"bookingEndDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldHandleDateTimeWithOffset() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDateTime", new DateTimeSchema());
        propertyTypes.put("endDateTime", new DateTimeSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDateTime", "endDateTime"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDateTime\": \"2024-01-01T10:00:00+02:00\", \"endDateTime\": \"2024-12-31T18:00:00+02:00\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verify(executor, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    void shouldNotRunWhenOnlyStartValueIsNotSet() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"endDate\": \"2024-12-31\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }

    @Test
    void shouldNotRunWhenOnlyEndValueIsNotSet() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> propertyTypes = new HashMap<>();
        propertyTypes.put("startDate", new DateSchema());
        propertyTypes.put("endDate", new DateSchema());

        Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("startDate", "endDate"));
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(propertyTypes);
        Mockito.when(data.getPayload()).thenReturn("{\"startDate\": \"2024-01-01\"}");

        dateRangeInversionFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(executor);
    }
}
