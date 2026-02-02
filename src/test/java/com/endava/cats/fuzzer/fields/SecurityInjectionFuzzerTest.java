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
        void shouldReturnAllInjectionTypes() {
            Assertions.assertThat(ldapFuzzer.getAllInjectionPayloads()).hasSizeGreaterThan(5);
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
                    Arguments.of("LDAP Error", 200,
                            "some content... javax.naming.NameNotFoundException ...",
                            "LDAP injection", true),
                    Arguments.of("Directory Services Error", 200,
                            "Active Directory error occurred",
                            "LDAP injection", true),
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
                    Arguments.of("Passwd file", 200,
                            "root:x:0:0:root:/root:/bin/bash",
                            "XXE vulnerability", true),
                    Arguments.of("Win ini", 200,
                            "[mci extensions]",
                            "XXE vulnerability", true),
                    Arguments.of("XML Parse Error", 200,
                            "saxparseexception",
                            "XXE vulnerability", true),
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
                    Arguments.of("Calculation Result 8051", 200,
                            "Value is 8051",
                            "Potential SSTI", true),
                    Arguments.of("Template Error", 200,
                            "Freemarker parsing error...",
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
