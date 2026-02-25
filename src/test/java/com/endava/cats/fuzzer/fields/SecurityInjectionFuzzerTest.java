package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class SecurityInjectionFuzzerTest {

    private ServiceCaller serviceCaller;
    @InjectSpy
    TestCaseListener testCaseListener;
    private SimpleExecutor simpleExecutor;
    private SecurityFuzzerArguments securityFuzzerArguments;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        simpleExecutor = new SimpleExecutor(testCaseListener, serviceCaller);
        securityFuzzerArguments = new SecurityFuzzerArguments();
    }

    @Nested
    @QuarkusTest
    class SqlInjectionFuzzerTest {
        private SqlInjectionInStringFieldsFuzzer sqlInjectionFuzzer;

        @BeforeEach
        void setup() {
            sqlInjectionFuzzer = new SqlInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(sqlInjectionFuzzer.description()).containsIgnoringCase("SQL injection");
        }

        @Test
        void shouldHaveToString() {
            Assertions.assertThat(sqlInjectionFuzzer).hasToString("SqlInjectionInStringFieldsFuzzer");
        }

        @Test
        void shouldSkipForHeadAndTrace() {
            Assertions.assertThat(sqlInjectionFuzzer.skipForHttpMethods())
                    .containsOnly(HttpMethod.HEAD, HttpMethod.TRACE);
        }

        @ParameterizedTest
        @CsvSource(value = {"''", "null"}, nullValues = "null")
        void shouldNotRunWithEmptyPayload(String payload) {
            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getPayload()).thenReturn(payload);
            sqlInjectionFuzzer.fuzz(data);
            Mockito.verifyNoInteractions(serviceCaller);
        }

        @Test
        void shouldNotRunWhenNoStringFields() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("count", new IntegerSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("count"));
            Mockito.when(data.getPayload()).thenReturn("{\"count\": 10}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);

            sqlInjectionFuzzer.fuzz(data);
            Mockito.verifyNoInteractions(serviceCaller);
        }

        @Test
        void shouldRunWhenStringFieldPresent() {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.times(10)).call(Mockito.any());
        }

        @Test
        void shouldRunAllPayloadsWhenFlagEnabled() {
            securityFuzzerArguments.setIncludeAllInjectionPayloads(true);
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sqlInjectionFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.times(30)).call(Mockito.any());
        }

        static Stream<Arguments> sqlInjectionScenarios() {
            return Stream.of(
                    Arguments.of("UNION SELECT with schema", 200,
                            "1 UNION SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='public'",
                            "SQL injection", true),
                    Arguments.of("UNION SELECT with error", 200,
                            "1 UNION SELECT NULL FROM information_schema.tables -- \nYou have an error in your SQL syntax",
                            "SQL injection", true),
                    Arguments.of("Schema details with error", 200,
                            "table_name: users, column_name: password\nSQL syntax error near 'SELECT'",
                            "SQL injection", true),
                    Arguments.of("Only UNION SELECT reflection", 200,
                            "Your input: ' UNION SELECT NULL--",
                            "accepted", false),
                    Arguments.of("Only schema keyword", 200,
                            "information_schema",
                            "accepted", false),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sqlInjectionScenarios")
        void shouldHandleSqlInjectionScenarios(String scenario, int responseCode, String responseBody,
                                               String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            sqlInjectionFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class NoSqlInjectionFuzzerTest {
        private NoSqlInjectionInStringFieldsFuzzer noSqlFuzzer;

        @BeforeEach
        void setup() {
            noSqlFuzzer = new NoSqlInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(noSqlFuzzer.description()).containsIgnoringCase("NoSQL injection");
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(noSqlFuzzer.getAllInjectionPayloads()).containsAll(NoSqlInjectionInStringFieldsFuzzer.ALL_NOSQL_INJECTION_PAYLOADS);
        }

        static Stream<Arguments> noSqlInjectionScenarios() {
            return Stream.of(
                    // Executable operator tests - covering all operator branches
                    Arguments.of("$where with function and return", 200,
                            "{\"$where\": \"function() { return this.password; }\", \"_id\": \"507f1f77bcf86cd799439011\"}",
                            "NoSQL injection", true),
                    Arguments.of("$regex with function", 200,
                            "{\"$regex\": \".*\", \"function\": \"return true\", \"bson\": \"data\"}",
                            "NoSQL injection", true),
                    Arguments.of("$gt with this. reference", 200,
                            "{\"$gt\": 0, \"return\": \"this.value\", \"mongodb\": \"info\"}",
                            "NoSQL injection", true),
                    Arguments.of("$gte with function", 200,
                            "{\"$gte\": 1, \"function\": \"test\", \"collection\": \"users\"}",
                            "NoSQL injection", true),
                    Arguments.of("$lt with return", 200,
                            "{\"$lt\": 100, \"return\": \"data\", \"db.\": \"query\"}",
                            "NoSQL injection", true),
                    Arguments.of("$ne with this.", 200,
                            "{\"$ne\": null, \"this.\": \"field\", \"pipeline\": \"data\"}",
                            "NoSQL injection", true),
                    Arguments.of("$nin with function", 200,
                            "{\"$nin\": [], \"function\": \"check\", \"objectid\": \"123\"}",
                            "NoSQL injection", true),
                    Arguments.of("$or with return", 200,
                            "{\"$or\": [], \"return\": \"value\", \"_id\": \"abc\"}",
                            "NoSQL injection", true),
                    Arguments.of("$and with this.", 200,
                            "{\"$and\": [], \"this.\": \"prop\", \"bson\": \"type\"}",
                            "NoSQL injection", true),
                    // MongoDB internals tests - covering all internal structure branches
                    Arguments.of("objectid with error", 200,
                            "{\"objectid\": \"507f1f77bcf86cd799439011\", \"error\": \"query failed\"}",
                            "NoSQL injection", true),
                    Arguments.of("_id with mongoerror", 200,
                            "{\"_id\": \"507f1f77bcf86cd799439011\", \"mongoerror\": \"unknown operator\"}",
                            "NoSQL injection", true),
                    Arguments.of("bson with mongodb error", 200,
                            "{\"bson\": \"data\", \"mongodb error\": \"failed\"}",
                            "NoSQL injection", true),
                    Arguments.of("mongodb with cannot read property", 200,
                            "{\"mongodb\": \"info\", \"cannot read property\": \"undefined\"}",
                            "NoSQL injection", true),
                    Arguments.of("collection with unexpected token", 200,
                            "{\"collection\": \"users\", \"unexpected token\": \"{\"}",
                            "NoSQL injection", true),
                    Arguments.of("db. with invalid operator", 200,
                            "{\"db.\": \"query\", \"invalid operator\": \"$test\"}",
                            "NoSQL injection", true),
                    Arguments.of("pipeline with unknown operator", 200,
                            "{\"pipeline\": \"agg\", \"unknown operator\": \"$custom\"}",
                            "NoSQL injection", true),
                    // Database error tests - covering all error message branches
                    Arguments.of("Operator with bad query", 200,
                            "{\"$where\": \"function() { return true; }\", \"bad query\": \"syntax error\"}",
                            "NoSQL injection", true),
                    Arguments.of("Internals with errmsg", 200,
                            "{\"_id\": \"123\", \"errmsg\": \"operation failed\"}",
                            "NoSQL injection", true),
                    // Single indicator tests (should not report vulnerability)
                    Arguments.of("Only $where operator (no JS)", 200,
                            "{\"$where\": \"1==1\"}",
                            "accepted", false),
                    Arguments.of("Only function keyword (no operator)", 200,
                            "{\"function\": \"test\"}",
                            "accepted", false),
                    Arguments.of("Only MongoDB internals", 200,
                            "{\"_id\": \"507f1f77bcf86cd799439011\"}",
                            "accepted", false),
                    Arguments.of("Only error message", 200,
                            "{\"error\": \"MongoError: unknown\"}",
                            "accepted", false),
                    Arguments.of("Payload reflection", 200,
                            "{\"query\": \"{\\\"$where\\\": \\\"1==1\\\"}\"}",
                            "accepted", false),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("noSqlInjectionScenarios")
        void shouldHandleNoSqlInjectionScenarios(String scenario, int responseCode, String responseBody,
                                                 String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("query", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("query"));
            Mockito.when(data.getPayload()).thenReturn("{\"query\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            noSqlFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class CommandInjectionFuzzerTest {
        private CommandInjectionInStringFieldsFuzzer commandFuzzer;

        @BeforeEach
        void setup() {
            commandFuzzer = new CommandInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(commandFuzzer.getAllInjectionPayloads()).containsAll(CommandInjectionInStringFieldsFuzzer.ALL_COMMAND_INJECTION_PAYLOADS);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(commandFuzzer.description()).containsIgnoringCase("Command injection");
        }

        static Stream<Arguments> commandInjectionScenarios() {
            return Stream.of(
                    Arguments.of("passwd file content", 200,
                            "root:x:0:0:root:/root:/bin/bash",
                            "command injection", true),
                    Arguments.of("id command output", 200,
                            "uid=0(root) gid=0(root) groups=0(root)",
                            "command injection", true),
                    Arguments.of("ls command output", 200,
                            "total 12\n-rw-r--r--  1 user  staff  1024 Dec 23 10:00 secrets.txt",
                            "command injection", true),
                    Arguments.of("uname command output", 200,
                            "Linux example-host 5.15.0-50-generic #56-Ubuntu SMP x86_64 GNU/Linux kernel info",
                            "command injection", true),
                    Arguments.of("Windows dir output", 200,
                            "Volume in drive C has no label.\nVolume Serial Number is 1234-ABCD\n\nDirectory of C:\\temp",
                            "command injection", true),
                    Arguments.of("Multiple indicators", 200,
                            "uid=0(root) gid=0(root) groups=0(root)\nroot:x:0:0:root:/root:/bin/bash",
                            "Command injection vulnerability detected", true),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("commandInjectionScenarios")
        void shouldHandleCommandInjectionScenarios(String scenario, int responseCode, String responseBody,
                                                   String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("filename", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("filename"));
            Mockito.when(data.getPayload()).thenReturn("{\"filename\": \"test.txt\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            commandFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class XssInjectionFuzzerTest {
        private XssInjectionInStringFieldsFuzzer xssFuzzer;

        @BeforeEach
        void setup() {
            xssFuzzer = new XssInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(xssFuzzer.description()).containsIgnoringCase("XSS");
        }

        @Test
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(xssFuzzer.getAllInjectionPayloads()).containsAll(XssInjectionInStringFieldsFuzzer.ALL_XSS_PAYLOADS);
        }

        static Stream<Arguments> xssInjectionScenarios() {
            return Stream.of(
                    Arguments.of("script tag reflected", 200,
                            "<script>alert('XSS')</script>",
                            "XSS payload reflected", true, true),
                    Arguments.of("img with onerror reflected", 200,
                            "<img src=x onerror=alert('XSS')>",
                            "XSS payload reflected", true, true),
                    Arguments.of("svg with onload reflected", 200,
                            "<svg onload=alert('XSS')>",
                            "XSS payload reflected", true, true),
                    Arguments.of("iframe with javascript reflected", 200,
                            "<iframe src=\"javascript:alert('XSS')\">",
                            "XSS payload reflected", true, true),
                    Arguments.of("onmouseover reflected", 200,
                            "\"onmouseover=\"alert(1)\"",
                            "XSS payload reflected", true, true),
                    Arguments.of("onfocus reflected", 200,
                            "<input onfocus=alert(1) autofocus>",
                            "XSS payload reflected", true, true),
                    Arguments.of("Clean response with 2xx", 200,
                            "{\"status\": \"ok\"}",
                            "XSS payload accepted", true, true),
                    Arguments.of("Payload not reflected", 200,
                            "{\"description\": \"safe content\"}",
                            "XSS payload accepted", true, true),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false, false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true, false)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("xssInjectionScenarios")
        void shouldHandleXssInjectionScenarios(String scenario, int responseCode, String responseBody,
                                               String expectedMessageFragment, boolean shouldBeWarnOrError,
                                               boolean shouldBeWarn) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("description", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("description"));
            Mockito.when(data.getPayload()).thenReturn("{\"description\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            xssFuzzer.fuzz(data);

            if (shouldBeWarnOrError) {
                if (shouldBeWarn) {
                    Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultWarn(
                            Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                            Mockito.anyString(), Mockito.any());
                } else {
                    Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                            Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                            Mockito.anyString(), Mockito.any());
                }
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class LdapInjectionFuzzerTest {
        private LdapInjectionInStringFieldsFuzzer ldapFuzzer;

        @BeforeEach
        void setup() {
            ldapFuzzer = new LdapInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(ldapFuzzer.description()).containsIgnoringCase("LDAP");
        }

        @Test
        void shouldReturnAllInjectionPayloads() {
            Assertions.assertThat(ldapFuzzer.getAllInjectionPayloads())
                    .hasSizeGreaterThan(50)
                    .contains("*)(&", "*)(uid=*))(|(uid=*", "admin)(&(password=*))");
        }
        
        @Test
        void shouldReturnTopInjectionPayloads() {
            Assertions.assertThat(ldapFuzzer.getTopInjectionPayloads())
                    .hasSize(8)
                    .contains("*)(&", "*)(uid=*))(|(uid=*", "admin)(&(password=*))");
        }
        
        @Test
        void shouldRunAllPayloadsWhenFlagEnabled() {
            securityFuzzerArguments.setIncludeAllInjectionPayloads(true);
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            ldapFuzzer.fuzz(data);

            int expectedCalls = ldapFuzzer.getAllInjectionPayloads().size();
            Mockito.verify(serviceCaller, Mockito.times(expectedCalls)).call(Mockito.any());
        }

        @ParameterizedTest
        @CsvSource({
                "username,true",
                "user_id,true",
                "dn,true",
                "my_cn,true",
                "uuid,true",
                "filterQuery,true",
                "address,false",
                "city,false",
                "email,true"
        })
        void shouldFuzzFieldBasedOnName(String fieldName, boolean shouldFuzz) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put(fieldName, new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(fieldName));
            Mockito.when(data.getPayload()).thenReturn("{\"" + fieldName + "\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            ldapFuzzer.fuzz(data);

            if (shouldFuzz) {
                Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
            } else {
                Mockito.verifyNoInteractions(serviceCaller);
            }
        }

        static Stream<Arguments> ldapInjectionScenarios() {
            return Stream.of(
                    // LDAP error keywords detection
                    Arguments.of("Java LDAP NamingException", 200,
                            "Error occurred: javax.naming.NameNotFoundException at line 42",
                            "LDAP injection", true),
                    Arguments.of("Java LDAP InvalidNameException", 200,
                            "Failed with error: javax.naming.InvalidNameException - invalid DN syntax",
                            "LDAP injection", true),
                    Arguments.of(".NET DirectoryServices error", 200,
                            "Exception: System.DirectoryServices.Protocols.LdapException occurred",
                            "LDAP injection", true),
                    Arguments.of("Python LDAP error", 200,
                            "ldap.filter_error: invalid search filter syntax",
                            "LDAP injection", true),
                    Arguments.of("PHP LDAP function error", 200,
                            "Warning: ldap_search() failed with error",
                            "LDAP injection", true),
                    Arguments.of("Invalid search filter error", 200,
                            "Error: invalid search filter - unbalanced parentheses in filter",
                            "LDAP injection", true),
                    Arguments.of("Malformed DN error", 200,
                            "invalid distinguished name syntax detected",
                            "LDAP injection", true),
                    Arguments.of("LDAP operations error", 200,
                            "LDAP operations error: unwilling to perform",
                            "LDAP injection", true),
                    Arguments.of("Active Directory DSID error", 200,
                            "Active Directory error DSID-0C0906E8 occurred",
                            "LDAP injection", true),
                    Arguments.of("OpenLDAP slapd error", 200,
                            "slapd: invalid filter syntax detected",
                            "LDAP injection", true),
                    Arguments.of("LDAP error code", 200,
                            "Error: LDAP code: 32 - no such object",
                            "LDAP injection", true),
                    
                    // Multiple LDAP success indicators (4+ required)
                    Arguments.of("Multiple LDAP attributes", 200,
                            "{\"distinguishedName\":\"cn=admin,dc=example,dc=com\",\"cn\":\"admin\",\"uid\":\"admin\",\"ou\":\"users\",\"objectClass\":\"person\"}",
                            "Potential LDAP injection", true),
                    Arguments.of("LDAP marker with attributes", 200,
                            "ldap_test_8051 cn=admin uid=root mail=admin@test.com memberOf=admins",
                            "Potential LDAP injection", true),
                    Arguments.of("LDIF format indicators", 200,
                            "dn: cn=admin,dc=test objectclass: person cn: admin uid: admin mail: admin@test samaccountname: admin",
                            "Potential LDAP injection", true),
                    
                    // Authentication bypass detection
                    Arguments.of("Login successful indicator", 200,
                            "{\"status\":\"login successful\",\"details\":\"cn=admin,dc=example,dc=com\"}",
                            "Potential LDAP authentication bypass", true),
                    Arguments.of("Authentication successful", 200,
                            "authentication successful - welcome admin cn=admin,dc=example,dc=com",
                            "Potential LDAP authentication bypass", true),
                    Arguments.of("Session created indicator", 200,
                            "session created successfully, access granted cn=admin,dc=example,dc=com",
                            "Potential LDAP authentication bypass", true),
                    Arguments.of("Authorized access", 200,
                            "User authorized, logged in successfully cn=admin,dc=example,dc=com",
                            "Potential LDAP authentication bypass", true),

                    // LDIF structure detection - these responses have enough attributes to trigger multiple attributes detection first
                    // Note: The multiple attributes check (4+ indicators) runs before LDIF structure check
                    Arguments.of("LDIF with dn and attributes", 200,
                            "dn: cn=admin,dc=example,dc=com\ncn: admin\nuid: admin\nobjectclass: person\nmail: admin@test.com\nsn: Admin",
                            "Potential LDAP injection detected", true),
                    Arguments.of("LDIF with changetype", 200,
                            "objectclass: person\nchangetype: add\ndn: cn=test\ncn: test\nuid: test",
                            "Potential LDAP injection detected", true),

                    // Large response with multiple user indicators
                    Arguments.of("Large response with user patterns", 200,
                            generateLargeResponseWithUserPatterns(),
                            "Potential LDAP injection", true),

                    // Error context validation (should not trigger without error context)
                    Arguments.of("LDAP keyword without error context", 200,
                            "{\"javax.naming.directory\":\"field_value\"}",
                            "accepted", false),
                    Arguments.of("Has 5 success indicators (cn=, uid=, mail=, cn=admin, uid=admin)", 200,
                            "distinguishedName=cn=admin,ou=users,dc=example,dc=com cn=admin uid=admin mail=admin@test.com memberOf=admins",
                            "Potential LDAP injection", true),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }
        
        private static String generateLargeResponseWithUserPatterns() {
            StringBuilder sb = new StringBuilder("[");
            // Need > 8 patterns total OR array structure with > 4 patterns
            // Patterns counted: "uid":, "cn":, "distinguishedname":, "samaccountname":
            for (int i = 0; i < 3; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"uid\":\"user").append(i)
                  .append("\",\"cn\":\"User ").append(i)
                  .append("\",\"distinguishedName\":\"cn=user").append(i)
                  .append(",dc=test\",\"samAccountName\":\"user").append(i).append("\"}")
                  .append("x".repeat(17000)); // Make it > 50000 bytes
            }
            sb.append("]");
            return sb.toString();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("ldapInjectionScenarios")
        void shouldHandleLdapInjectionScenarios(String scenario, int responseCode, String responseBody,
                                                String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("username", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("username"));
            Mockito.when(data.getPayload()).thenReturn("{\"username\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            ldapFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class XxeInjectionFuzzerTest {
        private XxeInjectionInStringFieldsFuzzer xxeFuzzer;

        @BeforeEach
        void setup() {
            xxeFuzzer = new XxeInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(xxeFuzzer.description()).containsIgnoringCase("XXE");
        }
        
        @Test
        void shouldReturnAllInjectionPayloads() {
            Assertions.assertThat(xxeFuzzer.getAllInjectionPayloads())
                    .hasSizeGreaterThan(15)
                    .anyMatch(payload -> payload.contains("XXE_TEST_SUCCESS_8051"))
                    .anyMatch(payload -> payload.contains("file:///etc/passwd"));
        }
        
        @Test
        void shouldReturnTopInjectionPayloads() {
            Assertions.assertThat(xxeFuzzer.getTopInjectionPayloads())
                    .hasSize(5)
                    .anyMatch(payload -> payload.contains("XXE_TEST_SUCCESS"))
                    .anyMatch(payload -> payload.contains("file:///etc/hostname"));
        }
        
        @Test
        void shouldRunAllPayloadsWhenFlagEnabled() {
            securityFuzzerArguments.setIncludeAllInjectionPayloads(true);
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("xmlBody", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("xmlBody"));
            Mockito.when(data.getPayload()).thenReturn("{\"xmlBody\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            xxeFuzzer.fuzz(data);

            int expectedCalls = xxeFuzzer.getAllInjectionPayloads().size();
            Mockito.verify(serviceCaller, Mockito.times(expectedCalls)).call(Mockito.any());
        }

        @ParameterizedTest
        @CsvSource({
                "xmlBody,true",
                "content,true",
                "userData,true",
                "payload,true",
                "name,false",
                "address,false"
        })
        void shouldFuzzFieldBasedOnName(String fieldName, boolean shouldFuzz) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put(fieldName, new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(fieldName));
            Mockito.when(data.getPayload()).thenReturn("{\"" + fieldName + "\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            xxeFuzzer.fuzz(data);

            if (shouldFuzz) {
                Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
            } else {
                Mockito.verifyNoInteractions(serviceCaller);
            }
        }

        static Stream<Arguments> xxeInjectionScenarios() {
            return Stream.of(
                    // XXE success markers detection
                    Arguments.of("XXE test success marker", 200,
                            "Response contains: xxe_test_success_8051",
                            "XXE vulnerability detected", true),
                    Arguments.of("XXE marker 6497", 200,
                            "Value: xxe_marker_6497 returned",
                            "XXE vulnerability detected", true),
                    Arguments.of("Unix passwd file content", 200,
                            "root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\nbin:x:2:2:bin:/bin:/usr/sbin/nologin",
                            "XXE vulnerability detected", true),
                    Arguments.of("Localhost hostname", 200,
                            "hostname: localhost",
                            "XXE vulnerability detected", true),
                    Arguments.of("Windows win.ini file", 200,
                            "[fonts]\n[extensions]\n[mci extensions]",
                            "XXE vulnerability detected", true),
                    Arguments.of("Windows hosts file", 200,
                            "# Copyright (c) 1993-2009 Microsoft Corp.\n127.0.0.1 localhost",
                            "XXE vulnerability detected", true),
                    Arguments.of("Environment variables", 200,
                            "PATH=/usr/local/bin:/usr/bin\nHOME=/root\nJAVA_HOME=/usr/lib/jvm/java-11",
                            "XXE vulnerability detected", true),
                    Arguments.of("Entity expansion result", 200,
                            "lollollollollollollollollollollollollollollollollollollollollollollollollollollollollol",
                            "XXE vulnerability detected", true),
                    Arguments.of("Repeated entity expansion", 200,
                            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "XXE vulnerability detected", true),
                    
                    // XXE error keywords detection
                    Arguments.of("XML parsing error", 200,
                            "Error: xml parsing error occurred at line 5",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("SAXParseException", 200,
                            "org.xml.sax.SAXParseException: Content is not allowed in prolog",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("SAXReader error", 200,
                            "SAXReader failed to parse document",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("DocumentBuilder exception", 200,
                            "javax.xml.parsers.DocumentBuilder threw exception",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("XMLStreamException", 200,
                            "javax.xml.stream.XMLStreamException: Unexpected character",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("XMLReader error", 200,
                            "XMLReader: error processing external entity",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Entity reference error", 200,
                            "External entity reference not allowed",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("DOCTYPE error", 200,
                            "DOCTYPE declaration is not allowed",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("DTD processing error", 200,
                            "DTD processing is prohibited for security reasons",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Unexpected token error", 200,
                            "Parsing failed: unexpected token '<' at position 10",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Content not allowed in prolog", 200,
                            "Parse error: content is not allowed in prolog",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Premature end of file", 200,
                            "XML error: premature end of file encountered",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Malformed XML", 200,
                            "Error: malformed XML document",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Invalid XML", 200,
                            "invalid xml structure detected",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Access denied error", 200,
                            "Security exception: access denied to external resource",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Permission denied", 200,
                            "permission denied: cannot read file",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Security restriction", 200,
                            "Security policy prohibits external entity access",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Entity expansion blocked", 200,
                            "Entity expansion limit exceeded - blocked",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("JAXP error", 200,
                            "javax.xml.parsers.JAXP configuration error",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Xerces parser error", 200,
                            "org.apache.xerces.parsers error occurred",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("libxml error", 200,
                            "libxml2 error: failed to parse document",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("MSXML error", 200,
                            "MSXML parser error: invalid syntax",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("File not found error", 200,
                            "Error: file not found - /etc/passwd",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("No such file", 200,
                            "no such file or directory: /etc/shadow",
                            "Potential XXE vulnerability detected", true),
                    Arguments.of("Cannot access file", 200,
                            "cannot access /c:/windows/win.ini",
                            "Potential XXE vulnerability detected", true),
                    
                    // XXE DoS detection (large response with repeated patterns)
                    Arguments.of("Large response with repeated lol pattern", 200,
                            generateLargeRepeatedPattern("lol", 25),
                            "Potential XXE DoS vulnerability detected", true),
                    Arguments.of("Large response with repeated aaa pattern", 200,
                            generateLargeRepeatedPattern("aaa", 30),
                            "Potential XXE DoS vulnerability detected", true),
                    Arguments.of("Large response with repeated test pattern", 200,
                            generateLargeRepeatedPattern("test", 22),
                            "Potential XXE DoS vulnerability detected", true),
                    Arguments.of("Large response with repeated xxx pattern", 200,
                            generateLargeRepeatedPattern("xxx", 21),
                            "Potential XXE DoS vulnerability detected", true),
                    
                    // Negative cases
                    Arguments.of("Small response with repeated pattern", 200,
                            "lol lol lol lol lol",
                            "accepted", false),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }
        
        private static String generateLargeRepeatedPattern(String pattern, int count) {
            StringBuilder sb = new StringBuilder();
            sb.append("x".repeat(50000)); // Make it > 100000 bytes
            for (int i = 0; i < count; i++) {
                sb.append(pattern).append(" ");
            }
            sb.append("y".repeat(50100));
            return sb.toString();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("xxeInjectionScenarios")
        void shouldHandleXxeInjectionScenarios(String scenario, int responseCode, String responseBody,
                                               String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("xmlBody", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("xmlBody"));
            Mockito.when(data.getPayload()).thenReturn("{\"xmlBody\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            xxeFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }

    @Nested
    @QuarkusTest
    class SstiInjectionFuzzerTest {
        private SstiInjectionInStringFieldsFuzzer sstiFuzzer;

        @BeforeEach
        void setup() {
            sstiFuzzer = new SstiInjectionInStringFieldsFuzzer(simpleExecutor, testCaseListener, securityFuzzerArguments);
        }

        @Test
        void shouldHaveDescription() {
            Assertions.assertThat(sstiFuzzer.description()).containsIgnoringCase("SSTI");
        }
        
        @Test
        void shouldReturnAllInjectionPayloads() {
            Assertions.assertThat(sstiFuzzer.getAllInjectionPayloads())
                    .hasSizeGreaterThan(15)
                    .anyMatch(payload -> payload.contains("83*97"))
                    .anyMatch(payload -> payload.contains("73*89"))
                    .anyMatch(payload -> payload.contains("${"))
                    .anyMatch(payload -> payload.contains("{{"));
        }
        
        @Test
        void shouldReturnTopInjectionPayloads() {
            Assertions.assertThat(sstiFuzzer.getTopInjectionPayloads())
                    .hasSize(5)
                    .anyMatch(payload -> payload.contains("83*97"))
                    .anyMatch(payload -> payload.contains("${"))
                    .anyMatch(payload -> payload.contains("{{"));
        }
        
        @Test
        void shouldRunAllPayloadsWhenFlagEnabled() {
            securityFuzzerArguments.setIncludeAllInjectionPayloads(true);
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("template", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("template"));
            Mockito.when(data.getPayload()).thenReturn("{\"template\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sstiFuzzer.fuzz(data);

            int expectedCalls = sstiFuzzer.getAllInjectionPayloads().size();
            Mockito.verify(serviceCaller, Mockito.times(expectedCalls)).call(Mockito.any());
        }

        @Test
        void shouldFuzzAnyStringField() {
            String fieldName = "anyField";
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put(fieldName, new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of(fieldName));
            Mockito.when(data.getPayload()).thenReturn("{\"" + fieldName + "\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).build());

            sstiFuzzer.fuzz(data);

            Mockito.verify(serviceCaller, Mockito.atLeastOnce()).call(Mockito.any());
        }

        static Stream<Arguments> sstiInjectionScenarios() {
            return Stream.of(
                    // Template error keywords detection
                    Arguments.of("Template keyword error", 200,
                            "Error in template processing at line 5",
                            "SSTI injection error", true),
                    Arguments.of("Expression error", 200,
                            "Expression evaluation failed: invalid syntax",
                            "SSTI injection error", true),
                    Arguments.of("Velocity error", 200,
                            "org.apache.velocity.exception.ParseErrorException occurred",
                            "SSTI injection error", true),
                    Arguments.of("Freemarker error", 200,
                            "freemarker.template.TemplateException: parsing error",
                            "SSTI injection error", true),
                    Arguments.of("Thymeleaf error", 200,
                            "org.thymeleaf.exceptions.TemplateProcessingException",
                            "SSTI injection error", true),
                    Arguments.of("Jinja error", 200,
                            "jinja2.exceptions.TemplateSyntaxError: unexpected token",
                            "SSTI injection error", true),
                    Arguments.of("Jinja2 error", 200,
                            "Jinja2 template error: undefined variable",
                            "SSTI injection error", true),
                    Arguments.of("Twig error", 200,
                            "Twig_Error_Syntax: Unknown function",
                            "SSTI injection error", true),
                    Arguments.of("Handlebars error", 200,
                            "Handlebars: template compilation failed",
                            "SSTI injection error", true),
                    Arguments.of("Mustache error", 200,
                            "Mustache template rendering error",
                            "SSTI injection error", true),
                    Arguments.of("Mako error", 200,
                            "mako.exceptions.SyntaxException occurred",
                            "SSTI injection error", true),
                    Arguments.of("Pebble error", 200,
                            "com.mitchellbosecke.pebble.error.ParserException",
                            "SSTI injection error", true),
                    Arguments.of("Groovy error", 200,
                            "groovy.text.TemplateExecutionException",
                            "SSTI injection error", true),
                    Arguments.of("ERB error", 200,
                            "ERB template compilation error",
                            "SSTI injection error", true),
                    Arguments.of("Razor error", 200,
                            "Razor parsing error: unexpected character",
                            "SSTI injection error", true),
                    Arguments.of("Smarty error", 200,
                            "Smarty: syntax error in template",
                            "SSTI injection error", true),
                    Arguments.of("Tornado error", 200,
                            "tornado.template.ParseError",
                            "SSTI injection error", true),
                    Arguments.of("Template engine error", 200,
                            "Template engine failed to process expression",
                            "SSTI injection error", true),
                    Arguments.of("Parsing error", 200,
                            "Template parsing error: invalid expression syntax",
                            "SSTI injection error", true),
                    Arguments.of("Syntax error", 200,
                            "Template syntax error at position 15",
                            "SSTI injection error", true),
                    Arguments.of("Render error", 200,
                            "Template render error: cannot evaluate expression",
                            "SSTI injection error", true),
                    Arguments.of("Compilation error", 200,
                            "Template compilation error occurred",
                            "SSTI injection error", true),
                    Arguments.of("Undefined variable error", 200,
                            "Error: undefined variable in template",
                            "SSTI injection error", true),
                    Arguments.of("Template not found error", 200,
                            "template not found: could not load template",
                            "SSTI injection error", true),
                    
                    // Calculation result detection (8051 = 83*97)
                    Arguments.of("Main calculation result 8051", 200,
                            "Result: 8051",
                            "Potential SSTI", true),
                    Arguments.of("8051 in JSON response", 200,
                            "{\"value\": 8051, \"status\": \"ok\"}",
                            "Potential SSTI", true),
                    Arguments.of("8051 in text context", 200,
                            "The calculated value is 8051 for this request",
                            "Potential SSTI", true),
                    Arguments.of("8051 with quotes", 200,
                            "\"result\": \"8051\"",
                            "Potential SSTI", true),
                    Arguments.of("8051 in HTML context", 200,
                            "<div>Value: 8051</div>",
                            "Potential SSTI", true),
                    Arguments.of("8051 with whitespace", 200,
                            "output: 8051 ",
                            "Potential SSTI", true),
                    
                    // Alternative calculation result detection (6497 = 73*89)
                    Arguments.of("Alternative calculation result 6497", 200,
                            "Result: 6497",
                            "Potential SSTI", true),
                    Arguments.of("6497 in JSON response", 200,
                            "{\"computed\": 6497}",
                            "Potential SSTI", true),
                    Arguments.of("6497 in text", 200,
                            "Evaluated to 6497",
                            "Potential SSTI", true),
                    
                    // Reflected payload with template indicators (requires both calc expr AND template keyword)
                    Arguments.of("Reflected calc expr with template error", 200,
                            "Your input: 83*97 caused a template parsing error",
                            "SSTI injection error", true),
                    Arguments.of("Reflected calc expr with engine keyword", 200,
                            "Expression 73*89 failed in template engine",
                            "SSTI injection error", true),
                    Arguments.of("Reflected with velocity keyword", 200,
                            "Input 83*97 triggered velocity error",
                            "SSTI injection error", true),
                    Arguments.of("Reflected with freemarker keyword", 200,
                            "Value 73*89 caused freemarker exception",
                            "SSTI injection error", true),
                    Arguments.of("Reflected with jinja keyword", 200,
                            "Expression 83*97 resulted in jinja2 error",
                            "SSTI injection error", true),
                    
                    // Edge cases for expression evaluation context
                    Arguments.of("8051 as part of larger number (should not detect)", 200,
                            "ID: 180512345",
                            "accepted", false),
                    Arguments.of("8051 without proper context (regex won't match)", 200,
                            "80518051",
                            "accepted", false),
                    Arguments.of("Reflected payload without template indicators", 200,
                            "Your input was: 83*97",
                            "accepted", false),
                    Arguments.of("Template keyword triggers error detection", 200,
                            "Template processing completed successfully",
                            "SSTI injection error", true),
                    Arguments.of("Clean response", 200,
                            "{\"status\": \"ok\"}",
                            "accepted", false),
                    Arguments.of("4xx rejection", 400,
                            "Bad Request",
                            "rejected", false),
                    Arguments.of("5xx error", 500,
                            "Internal Server Error",
                            "Server error", true)
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sstiInjectionScenarios")
        void shouldHandleSstiInjectionScenarios(String scenario, int responseCode, String responseBody,
                                                String expectedMessageFragment, boolean shouldBeError) {
            Map<String, Schema> reqTypes = new HashMap<>();
            reqTypes.put("template", new StringSchema());

            FuzzingData data = Mockito.mock(FuzzingData.class);
            Mockito.when(data.getAllFieldsByHttpMethod()).thenReturn(Set.of("template"));
            Mockito.when(data.getPayload()).thenReturn("{\"template\": \"test\"}");
            Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
            Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
            Mockito.when(data.getHeaders()).thenReturn(Set.of());
            Mockito.when(data.getFirstRequestContentType()).thenReturn("application/json");

            Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(
                    CatsResponse.builder()
                            .responseCode(responseCode)
                            .body(responseBody)
                            .build());

            sstiFuzzer.fuzz(data);

            if (shouldBeError) {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultError(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment),
                        Mockito.anyString(), Mockito.any());
            } else {
                Mockito.verify(testCaseListener, Mockito.atLeastOnce()).reportResultInfo(
                        Mockito.any(), Mockito.any(), Mockito.contains(expectedMessageFragment), Mockito.any());
            }
        }
    }
}
