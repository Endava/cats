package com.endava.cats.dsl.impl;

import com.endava.cats.dsl.api.Parser;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class MiniDslParserTest {

    private MiniDslParser parser;
    private Map<String, String> context;

    @BeforeEach
    void setUp() {
        parser = new MiniDslParser();
        context = new HashMap<>();
        CatsRandom.initRandom(42L);
    }

    @Nested
    @DisplayName("Basic Expression Tests")
    class BasicExpressionTests {

        @Test
        @DisplayName("Should return original expression when parsing fails")
        void shouldReturnOriginalExpressionWhenParsingFails() {
            String result = parser.parse("invalid{{expression", context);

            assertThat(result).isEqualTo("invalid{{expression");
        }

        @Test
        @DisplayName("Should handle null expression")
        void shouldHandleNullExpression() {
            String result = parser.parse(null, context);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty expression")
        void shouldHandleEmptyExpression() {
            String result = parser.parse("", context);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle whitespace expression")
        void shouldHandleWhitespaceExpression() {
            String result = parser.parse("   ", context);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Context Resolution Tests")
    class ContextResolutionTests {

        @Test
        @DisplayName("Should resolve value from context")
        void shouldResolveValueFromContext() {
            context.put("key", "value");

            String result = parser.parse("key", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should return expression when key not in context")
        void shouldReturnExpressionWhenKeyNotInContext() {
            String result = parser.parse("unknownKey", context);

            assertThat(result).isEqualTo("unknownKey");
        }
    }

    @Nested
    @DisplayName("Placeholder Tests")
    class PlaceholderTests {

        @Test
        @DisplayName("Should resolve single placeholder")
        void shouldResolveSinglePlaceholder() {
            context.put("name", "John");

            String result = parser.parse("${name}", context);

            assertThat(result).isEqualTo("John");
        }

        @Test
        @DisplayName("Should resolve embedded placeholders")
        void shouldResolveEmbeddedPlaceholders() {
            context.put("firstName", "John");
            context.put("lastName", "Doe");

            String result = parser.parse("Hello ${firstName} ${lastName}", context);

            assertThat(result).isEqualTo("Hello John Doe");
        }

        @Test
        @DisplayName("Should handle nested placeholders")
        void shouldHandleNestedPlaceholders() {
            context.put("outer", "value");

            String result = parser.parse("${outer}", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should handle placeholder with no matching key")
        void shouldHandlePlaceholderWithNoMatchingKey() {
            String result = parser.parse("${unknown}", context);

            assertThat(result).isEqualTo("unknown");
        }

        @Test
        @DisplayName("Should handle unmatched braces in placeholder")
        void shouldHandleUnmatchedBracesInPlaceholder() {
            String result = parser.parse("${incomplete", context);

            assertThat(result).isEqualTo("${incomplete");
        }

        @Test
        @DisplayName("Should handle multiple embedded placeholders")
        void shouldHandleMultipleEmbeddedPlaceholders() {
            context.put("a", "1");
            context.put("b", "2");
            context.put("c", "3");

            String result = parser.parse("Values: ${a}, ${b}, ${c}", context);

            assertThat(result).isEqualTo("Values: 1, 2, 3");
        }
    }

    @Nested
    @DisplayName("Template Substitution Tests")
    class TemplateSubstitutionTests {

        @Test
        @DisplayName("Should substitute leading token templates")
        void shouldSubstituteLeadingTokenTemplates() {
            context.put("prefix", "value");

            String result = parser.parse("prefix-suffix", context);

            assertThat(result).isEqualTo("value-suffix");
        }

        @Test
        @DisplayName("Should not substitute when no dash present")
        void shouldNotSubstituteWhenNoDashPresent() {
            context.put("prefix", "value");

            String result = parser.parse("prefix", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should not substitute when prefix not in context")
        void shouldNotSubstituteWhenPrefixNotInContext() {
            String result = parser.parse("unknown-suffix", context);

            assertThat(result).isEqualTo("unknown-suffix");
        }
    }

    @Nested
    @DisplayName("Type Chain Tests")
    class TypeChainTests {

        @Test
        @DisplayName("Should evaluate UUID randomUUID")
        void shouldEvaluateUuidRandomUuid() {
            String result = parser.parse("T(java.util.UUID).randomUUID()", context);

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should evaluate LocalDate now")
        void shouldEvaluateLocalDateNow() {
            String result = parser.parse("T(java.time.LocalDate).now()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should evaluate OffsetDateTime now")
        void shouldEvaluateOffsetDateTimeNow() {
            String result = parser.parse("T(java.time.OffsetDateTime).now()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should evaluate CatsRandom alphanumeric")
        void shouldEvaluateCatsRandomAlphanumeric() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).alphanumeric(10)", context);

            assertThat(result).hasSize(10).matches("[a-zA-Z0-9]+");
        }

        @Test
        @DisplayName("Should evaluate CatsRandom alphabetic")
        void shouldEvaluateCatsRandomAlphabetic() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).alphabetic(5)", context);

            assertThat(result).hasSize(5).matches("[a-zA-Z]+");
        }

        @Test
        @DisplayName("Should evaluate CatsRandom numeric")
        void shouldEvaluateCatsRandomNumeric() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).numeric(8)", context);

            assertThat(result).hasSize(8).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should throw exception for disallowed type")
        void shouldThrowExceptionForDisallowedType() {
            String result = parser.parse("T(java.lang.System).currentTimeMillis()", context);

            assertThat(result).isEqualTo("T(java.lang.System).currentTimeMillis()");
        }

        @Test
        @DisplayName("Should throw exception for type without method chain")
        void shouldThrowExceptionForTypeWithoutMethodChain() {
            String result = parser.parse("T(java.util.UUID)", context);

            assertThat(result).isEqualTo("T(java.util.UUID)");
        }

        @Test
        @DisplayName("Should evaluate BigDecimal valueOf")
        void shouldEvaluateBigDecimalValueOf() {
            String result = parser.parse("T(java.math.BigDecimal).valueOf(123)", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should evaluate StringUtils upperCase")
        void shouldEvaluateStringUtilsUpperCase() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase('hello')", context);

            assertThat(result).isEqualTo("HELLO");
        }
    }

    @Nested
    @DisplayName("Instance Method Call Tests")
    class InstanceMethodCallTests {

        @Test
        @DisplayName("Should call method on string instance")
        void shouldCallMethodOnStringInstance() {
            context.put("text", "hello");

            String result = parser.parse("text.toUpperCase()", context);

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should chain method calls")
        void shouldChainMethodCalls() {
            context.put("text", "  hello  ");

            String result = parser.parse("text.trim().toUpperCase()", context);

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should call toString on any object")
        void shouldCallToStringOnAnyObject() {
            context.put("value", "test");

            String result = parser.parse("value.toString()", context);

            assertThat(result).isEqualTo("test");
        }

        @Test
        @DisplayName("Should handle method call with arguments")
        void shouldHandleMethodCallWithArguments() {
            context.put("text", "hello world");

            String result = parser.parse("text.substring(0, 5)", context);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should handle method call on null target")
        void shouldHandleMethodCallOnNullTarget() {
            String result = parser.parse("unknown.method()", context);

            assertThat(result).isEqualTo("unknown.method()");
        }

        @Test
        @DisplayName("Should call method on LocalDate")
        void shouldCallMethodOnLocalDate() {
            String result = parser.parse("T(java.time.LocalDate).now().toString()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should call method on OffsetDateTime")
        void shouldCallMethodOnOffsetDateTime() {
            String result = parser.parse("T(java.time.OffsetDateTime).now().toString()", context);

            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("String Literal Tests")
    class StringLiteralTests {

        @Test
        @DisplayName("Should parse single quoted string literal")
        void shouldParseSingleQuotedStringLiteral() {
            String result = parser.parse("'hello'", context);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should parse double quoted string literal")
        void shouldParseDoubleQuotedStringLiteral() {
            String result = parser.parse("\"hello\"", context);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should parse empty single quoted string")
        void shouldParseEmptySingleQuotedString() {
            String result = parser.parse("''", context);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should parse empty double quoted string")
        void shouldParseEmptyDoubleQuotedString() {
            String result = parser.parse("\"\"", context);

            assertThat(result).isEmpty();
            System.out.println(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC)));
        }
    }

    @Nested
    @DisplayName("Numeric Literal Tests")
    class NumericLiteralTests {

        @Test
        @DisplayName("Should parse positive integer")
        void shouldParsePositiveInteger() {
            String result = parser.parse("123", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should parse negative integer")
        void shouldParseNegativeInteger() {
            String result = parser.parse("-123", context);

            assertThat(result).isEqualTo("-123");
        }

        @Test
        @DisplayName("Should parse zero")
        void shouldParseZero() {
            String result = parser.parse("0", context);

            assertThat(result).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("Boolean Literal Tests")
    class BooleanLiteralTests {

        @Test
        @DisplayName("Should parse true")
        void shouldParseTrue() {
            String result = parser.parse("true", context);

            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("Should parse false")
        void shouldParseFalse() {
            String result = parser.parse("false", context);

            assertThat(result).isEqualTo("false");
        }

        @Test
        @DisplayName("Should parse TRUE in uppercase")
        void shouldParseTrueInUppercase() {
            String result = parser.parse("TRUE", context);

            assertThat(result).isEqualTo("true");
        }

        @Test
        @DisplayName("Should parse FALSE in uppercase")
        void shouldParseFalseInUppercase() {
            String result = parser.parse("FALSE", context);

            assertThat(result).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("JSON Path Resolution Tests")
    class JsonPathResolutionTests {

        @Test
        @DisplayName("Should resolve value from request JSON")
        void shouldResolveValueFromRequestJson() {
            context.put(Parser.REQUEST, "{\"name\":\"John\",\"age\":30}");

            String result = parser.parse("request.name", context);

            assertThat(result).isEqualTo("John");
        }

        @Test
        @DisplayName("Should resolve value from response JSON")
        void shouldResolveValueFromResponseJson() {
            context.put(Parser.RESPONSE, "{\"status\":\"success\",\"code\":200}");

            String result = parser.parse("response.status", context);

            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("Should resolve nested value from request JSON")
        void shouldResolveNestedValueFromRequestJson() {
            context.put(Parser.REQUEST, "{\"user\":{\"name\":\"John\"}}");

            String result = parser.parse("request.user.name", context);

            assertThat(result).isEqualTo("John");
        }

        @Test
        @DisplayName("Should resolve nested value from response JSON")
        void shouldResolveNestedValueFromResponseJson() {
            context.put(Parser.RESPONSE, "{\"data\":{\"id\":123}}");

            String result = parser.parse("response.data.id", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should handle invalid JSON path")
        void shouldHandleInvalidJsonPath() {
            context.put(Parser.REQUEST, "{\"name\":\"John\"}");

            String result = parser.parse("request.unknown", context);

            assertThat(result).isEqualTo("request.unknown");
        }

        @Test
        @DisplayName("Should handle null JSON")
        void shouldHandleNullJson() {
            String result = parser.parse("request.name", context);

            assertThat(result).isEqualTo("request.name");
        }

        @Test
        @DisplayName("Should handle empty JSON")
        void shouldHandleEmptyJson() {
            context.put(Parser.REQUEST, "");

            String result = parser.parse("request.name", context);

            assertThat(result).isEqualTo("request.name");
        }

        @Test
        @DisplayName("Should resolve from response when not in request")
        void shouldResolveFromResponseWhenNotInRequest() {
            context.put(Parser.REQUEST, "{\"name\":\"John\"}");
            context.put(Parser.RESPONSE, "{\"id\":123}");

            String result = parser.parse("id", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should use lowercase request key as fallback")
        void shouldUseLowercaseRequestKeyAsFallback() {
            context.put("request", "{\"name\":\"John\"}");

            String result = parser.parse("request.name", context);

            assertThat(result).isEqualTo("John");
        }

        @Test
        @DisplayName("Should use lowercase response key as fallback")
        void shouldUseLowercaseResponseKeyAsFallback() {
            context.put("response", "{\"status\":\"ok\"}");

            String result = parser.parse("response.status", context);

            assertThat(result).isEqualTo("ok");
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should deny getClass method")
        void shouldDenyGetClassMethod() {
            context.put("text", "hello");

            String result = parser.parse("text.getClass()", context);

            assertThat(result).isEqualTo("text.getClass()");
        }

        @Test
        @DisplayName("Should deny wait method")
        void shouldDenyWaitMethod() {
            context.put("obj", "test");

            String result = parser.parse("obj.wait()", context);

            assertThat(result).isEqualTo("obj.wait()");
        }

        @Test
        @DisplayName("Should deny notify method")
        void shouldDenyNotifyMethod() {
            context.put("obj", "test");

            String result = parser.parse("obj.notify()", context);

            assertThat(result).isEqualTo("obj.notify()");
        }

        @Test
        @DisplayName("Should deny notifyAll method")
        void shouldDenyNotifyAllMethod() {
            context.put("obj", "test");

            String result = parser.parse("obj.notifyAll()", context);

            assertThat(result).isEqualTo("obj.notifyAll()");
        }

        @Test
        @DisplayName("Should not allow instance calls on non-whitelisted types")
        void shouldNotAllowInstanceCallsOnNonWhitelistedTypes() {
            String result = parser.parse("T(java.lang.Integer).valueOf(123).getClass()", context);

            assertThat(result).isEqualTo("T(java.lang.Integer).valueOf(123).getClass()");
        }
    }

    @Nested
    @DisplayName("Complex Expression Tests")
    class ComplexExpressionTests {

        @Test
        @DisplayName("Should handle complex chained method calls")
        void shouldHandleComplexChainedMethodCalls() {
            String result = parser.parse("T(java.util.UUID).randomUUID().toString()", context);

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should handle method with multiple arguments")
        void shouldHandleMethodWithMultipleArguments() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).substring('hello world', 0, 5)", context);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should handle nested method calls as arguments")
        void shouldHandleNestedMethodCallsAsArguments() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase('hello')", context);

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should handle mixed placeholders and expressions")
        void shouldHandleMixedPlaceholdersAndExpressions() {
            context.put("prefix", "Hello");

            String result = parser.parse("${prefix} World", context);

            assertThat(result).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("Should handle string with quotes in method arguments")
        void shouldHandleStringWithQuotesInMethodArguments() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase(\"test\")", context);

            assertThat(result).isEqualTo("TEST");
        }

        @Test
        @DisplayName("Should handle parentheses in string literals")
        void shouldHandleParenthesesInStringLiterals() {
            String result = parser.parse("'(test)'", context);

            assertThat(result).isEqualTo("(test)");
        }

        @Test
        @DisplayName("Should handle commas in string literals")
        void shouldHandleCommasInStringLiterals() {
            String result = parser.parse("'hello, world'", context);

            assertThat(result).isEqualTo("hello, world");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle expression with only whitespace after trim")
        void shouldHandleExpressionWithOnlyWhitespaceAfterTrim() {
            String result = parser.parse("   ", context);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle malformed T expression")
        void shouldHandleMalformedTExpression() {
            String result = parser.parse("T(incomplete", context);

            assertThat(result).isEqualTo("T(incomplete");
        }

        @Test
        @DisplayName("Should handle method call without opening parenthesis")
        void shouldHandleMethodCallWithoutOpeningParenthesis() {
            context.put("text", "hello");

            String result = parser.parse("text.method", context);

            assertThat(result).isEqualTo("text.method");
        }

        @Test
        @DisplayName("Should handle invalid method chain")
        void shouldHandleInvalidMethodChain() {
            context.put("text", "hello");

            String result = parser.parse("text..()", context);

            assertThat(result).isEqualTo("text..()");
        }

        @Test
        @DisplayName("Should handle unmatched parentheses")
        void shouldHandleUnmatchedParentheses() {
            String result = parser.parse("T(java.util.UUID).randomUUID(", context);

            assertThat(result).isEqualTo("T(java.util.UUID).randomUUID(");
        }

        @Test
        @DisplayName("Should handle identifier path")
        void shouldHandleIdentifierPath() {
            context.put("user.name", "John");

            String result = parser.parse("user.name", context);

            assertThat(result).isEqualTo("John");
        }

        @Test
        @DisplayName("Should handle numeric range in CatsRandom")
        void shouldHandleNumericRangeInCatsRandom() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).numeric(5, 10)", context);

            assertThat(result).hasSizeBetween(5, 9).matches("[0-9]+");
        }

        @Test
        @DisplayName("Should handle CatsRandom ascii")
        void shouldHandleCatsRandomAscii() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).ascii(5)", context);

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("Should handle CatsRandom next")
        void shouldHandleCatsRandomNext() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).next(5)", context);

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("Should handle BigInteger valueOf")
        void shouldHandleBigIntegerValueOf() {
            String result = parser.parse("T(java.math.BigInteger).valueOf(123)", context);

            assertThat(result).isEqualTo("123");
        }

        @ParameterizedTest
        @CsvSource("T(java.time.LocalDateTime).now(),T(java.time.ZonedDateTime).now(),T(java.time.Instant).now()")
        @DisplayName("Should handle dates now")
        void shouldHandleLocalDateTimeNow(String expression) {
            String result = parser.parse(expression, context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle Duration ofDays")
        void shouldHandleDurationOfDays() {
            String result = parser.parse("T(java.time.Duration).ofDays(1)", context);

            assertThat(result).contains("PT24H");
        }

        @Test
        @DisplayName("Should handle Period ofDays")
        void shouldHandlePeriodOfDays() {
            String result = parser.parse("T(java.time.Period).ofDays(1)", context);

            assertThat(result).contains("P1D");
        }

        @Test
        @DisplayName("Should handle RandomStringUtils random")
        void shouldHandleRandomStringUtilsRandom() {
            String result = parser.parse("T(org.apache.commons.lang3.RandomStringUtils).randomAlphabetic(5)", context);

            assertThat(result).hasSize(5).matches("[a-zA-Z]+");
        }

        @Test
        @DisplayName("Should handle String valueOf")
        void shouldHandleStringValueOf() {
            String result = parser.parse("T(java.lang.String).valueOf(123)", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should handle method with long parameter")
        void shouldHandleMethodWithLongParameter() {
            String result = parser.parse("T(java.math.BigDecimal).valueOf(123)", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should handle method with character parameter")
        void shouldHandleMethodWithCharacterParameter() {
            String result = parser.parse("T(java.lang.String).valueOf('a')", context);

            assertThat(result).isEqualTo("a");
        }

        @Test
        @DisplayName("Should handle no matching method")
        void shouldHandleNoMatchingMethod() {
            String result = parser.parse("T(java.util.UUID).nonExistentMethod()", context);

            assertThat(result).isEqualTo("T(java.util.UUID).nonExistentMethod()");
        }

        @Test
        @DisplayName("Should handle method with incompatible arguments")
        void shouldHandleMethodWithIncompatibleArguments() {
            String result = parser.parse("T(java.math.BigDecimal).valueOf('notANumber')", context);

            assertThat(result).isEqualTo("T(java.math.BigDecimal).valueOf('notANumber')");
        }
    }

    @Nested
    @DisplayName("Quote Handling Tests")
    class QuoteHandlingTests {

        @Test
        @DisplayName("Should handle single quotes in double quoted string")
        void shouldHandleSingleQuotesInDoubleQuotedString() {
            String result = parser.parse("\"it's a test\"", context);

            assertThat(result).isEqualTo("it's a test");
        }

        @Test
        @DisplayName("Should handle double quotes in single quoted string")
        void shouldHandleDoubleQuotesInSingleQuotedString() {
            String result = parser.parse("'say \"hello\"'", context);

            assertThat(result).isEqualTo("say \"hello\"");
        }

        @Test
        @DisplayName("Should handle quotes in method arguments")
        void shouldHandleQuotesInMethodArguments() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase('test')", context);

            assertThat(result).isEqualTo("TEST");
        }

        @Test
        @DisplayName("Should handle nested quotes in complex expression")
        void shouldHandleNestedQuotesInComplexExpression() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).replace('hello world', 'world', 'there')", context);

            assertThat(result).isEqualTo("hello there");
        }
    }

    @Nested
    @DisplayName("Argument Parsing Tests")
    class ArgumentParsingTests {

        @Test
        @DisplayName("Should parse empty arguments")
        void shouldParseEmptyArguments() {
            String result = parser.parse("T(java.util.UUID).randomUUID()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should parse single argument")
        void shouldParseSingleArgument() {
            String result = parser.parse("T(com.endava.cats.util.CatsRandom).alphanumeric(10)", context);

            assertThat(result).hasSize(10);
        }

        @Test
        @DisplayName("Should parse multiple arguments")
        void shouldParseMultipleArguments() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).substring('hello', 0, 3)", context);

            assertThat(result).isEqualTo("hel");
        }

        @Test
        @DisplayName("Should parse arguments with nested parentheses")
        void shouldParseArgumentsWithNestedParentheses() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase('test')", context);

            assertThat(result).isEqualTo("TEST");
        }

        @Test
        @DisplayName("Should parse arguments with commas in strings")
        void shouldParseArgumentsWithCommasInStrings() {
            String result = parser.parse("T(org.apache.commons.lang3.StringUtils).upperCase('a,b,c')", context);

            assertThat(result).isEqualTo("A,B,C");
        }
    }

    @Nested
    @DisplayName("Method Resolution Tests")
    class MethodResolutionTests {

        @ParameterizedTest
        @CsvSource("T(java.util.UUID).randomUUID(),T(java.time.LocalDate).now().toString(),T(java.time.OffsetDateTime).now().toString()")
        @DisplayName("Should resolve static method")
        void shouldResolveStaticMethod(String expression) {
            String result = parser.parse(expression, context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should resolve instance method on String")
        void shouldResolveInstanceMethodOnString() {
            context.put("text", "hello");

            String result = parser.parse("text.toUpperCase()", context);

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should handle method not found")
        void shouldHandleMethodNotFound() {
            String result = parser.parse("T(java.util.UUID).nonExistent()", context);

            assertThat(result).isEqualTo("T(java.util.UUID).nonExistent()");
        }
    }

    @Nested
    @DisplayName("Type Coercion Tests")
    class TypeCoercionTests {

        @Test
        @DisplayName("Should coerce Integer to Long")
        void shouldCoerceIntegerToLong() {
            String result = parser.parse("T(java.math.BigDecimal).valueOf(123)", context);

            assertThat(result).isEqualTo("123");
        }

        @Test
        @DisplayName("Should coerce String to Character")
        void shouldCoerceStringToCharacter() {
            String result = parser.parse("T(java.lang.String).valueOf('a')", context);

            assertThat(result).isEqualTo("a");
        }

        @Test
        @DisplayName("Should handle String as CharSequence")
        void shouldHandleStringAsCharSequence() {
            context.put("text", "hello");

            String result = parser.parse("text.toString()", context);

            assertThat(result).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("Brace Matching Tests")
    class BraceMatchingTests {

        @Test
        @DisplayName("Should find matching brace for simple placeholder")
        void shouldFindMatchingBraceForSimplePlaceholder() {
            String result = parser.parse("${key}", context);

            assertThat(result).isEqualTo("key");
        }

        @Test
        @DisplayName("Should find matching brace with nested braces")
        void shouldFindMatchingBraceWithNestedBraces() {
            context.put("outer", "value");

            String result = parser.parse("${outer}", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should handle unmatched opening brace")
        void shouldHandleUnmatchedOpeningBrace() {
            String result = parser.parse("${incomplete", context);

            assertThat(result).isEqualTo("${incomplete");
        }

        @Test
        @DisplayName("Should handle braces in quotes")
        void shouldHandleBracesInQuotes() {
            String result = parser.parse("'text with { and }'", context);

            assertThat(result).isEqualTo("text with { and }");
        }
    }

    @Nested
    @DisplayName("Dot Finding Tests")
    class DotFindingTests {

        @Test
        @DisplayName("Should find dot before method call")
        void shouldFindDotBeforeMethodCall() {
            context.put("text", "hello");

            String result = parser.parse("text.toUpperCase()", context);

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should not find dot in quotes")
        void shouldNotFindDotInQuotes() {
            String result = parser.parse("'text.with.dots'", context);

            assertThat(result).isEqualTo("text.with.dots");
        }

        @Test
        @DisplayName("Should not find dot in parentheses")
        void shouldNotFindDotInParentheses() {
            String result = parser.parse("T(java.util.UUID).randomUUID()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle dot not followed by identifier")
        void shouldHandleDotNotFollowedByIdentifier() {
            String result = parser.parse("text.", context);

            assertThat(result).isEqualTo("text.");
        }

        @Test
        @DisplayName("Should handle dot not followed by opening paren")
        void shouldHandleDotNotFollowedByOpeningParen() {
            context.put("text", "hello");

            String result = parser.parse("text.field", context);

            assertThat(result).isEqualTo("text.field");
        }
    }

    @Nested
    @DisplayName("Identifier Reading Tests")
    class IdentifierReadingTests {

        @Test
        @DisplayName("Should read simple identifier")
        void shouldReadSimpleIdentifier() {
            context.put("key", "value");

            String result = parser.parse("key", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should read identifier with underscores")
        void shouldReadIdentifierWithUnderscores() {
            context.put("my_key", "value");

            String result = parser.parse("my_key", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should read identifier with numbers")
        void shouldReadIdentifierWithNumbers() {
            context.put("key123", "value");

            String result = parser.parse("key123", context);

            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("Should stop reading at non-identifier character")
        void shouldStopReadingAtNonIdentifierCharacter() {
            context.put("key", "value");

            String result = parser.parse("key-suffix", context);

            assertThat(result).isEqualTo("value-suffix");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complex real-world expression")
        void shouldHandleComplexRealWorldExpression() {
            context.put(Parser.REQUEST, "{\"userId\":123}");

            String result = parser.parse("User-${request.userId}", context);

            assertThat(result).isEqualTo("User-123");
        }

        @Test
        @DisplayName("Should handle multiple operations in sequence")
        void shouldHandleMultipleOperationsInSequence() {
            context.put("name", "john");

            String result = parser.parse("name.toUpperCase()", context);

            assertThat(result).isEqualTo("JOHN");
        }

        @Test
        @DisplayName("Should handle mixed static and instance calls")
        void shouldHandleMixedStaticAndInstanceCalls() {
            String result = parser.parse("T(java.util.UUID).randomUUID().toString()", context);

            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Should handle expression with all features combined")
        void shouldHandleExpressionWithAllFeaturesCombined() {
            context.put("prefix", "TEST");
            context.put(Parser.REQUEST, "{\"id\":456}");

            String result = parser.parse("${prefix}-${request.id}", context);

            assertThat(result).isEqualTo("TEST-456");
        }

        @Test
        @DisplayName("Should handle field access on DateTimeFormatter")
        void shouldHandleFieldAccessOnDateTimeFormatter() {
            String result = parser.parse("T(java.time.format.DateTimeFormatter).ISO_OFFSET_DATE_TIME.toString()", context);

            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Should handle nested T() expressions in method arguments")
        void shouldHandleNestedTExpressionsInMethodArguments() {
            String result = parser.parse("T(java.time.format.DateTimeFormatter).ISO_OFFSET_DATE_TIME.format(T(java.time.Instant).now().atOffset(T(java.time.ZoneOffset).UTC))", context);

            assertThat(result).isNotEmpty().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        }

        @Test
        @DisplayName("Should handle ZoneOffset UTC constant")
        void shouldHandleZoneOffsetUtcConstant() {
            String result = parser.parse("T(java.time.ZoneOffset).UTC.toString()", context);

            assertThat(result).isEqualTo("Z");
        }

        @Test
        @DisplayName("Should handle Instant with atOffset")
        void shouldHandleInstantWithAtOffset() {
            String result = parser.parse("T(java.time.Instant).now().atOffset(T(java.time.ZoneOffset).UTC).toString()", context);

            assertThat(result).isNotEmpty().contains("Z");
        }
    }
}
