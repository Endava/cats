package com.endava.cats.report;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.ann.ExcludeTestCaseStrategy;
import com.endava.cats.util.HeaderPresentation;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.KeyValueSerializer;
import com.endava.cats.util.LongTypeSerializer;
import com.endava.cats.util.SyntheticAuthHeaderPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.Strictness;

/** Cached report serializers; the public-probe adapter applies only inside an eligible request. */
public final class TestCaseReportRenderer {
    private final Gson maskingSerializer;
    private final Gson probeRequestSerializer;
    private final Gson probeTestSerializer;
    private final HeaderPresentation headers;

    public TestCaseReportRenderer(ReportingArguments arguments) {
        headers = new HeaderPresentation(arguments);
        maskingSerializer = maskingSerializer(arguments);
        probeRequestSerializer = maskingSerializer.newBuilder()
                .registerTypeAdapter(KeyValuePair.class, new KeyValueSerializer(arguments, SyntheticAuthHeaderPolicy::isPublicHeader)).create();
        JsonSerializer<CatsRequest> requestAdapter = (request, _, _) -> probeRequestSerializer.toJsonTree(request);
        probeTestSerializer = maskingSerializer.newBuilder().registerTypeAdapter(CatsRequest.class, requestAdapter).create();
    }

    public Gson maskingSerializer() {
        return maskingSerializer;
    }

    public String json(CatsTestCase testCase) {
        return (SyntheticAuthHeaderPolicy.isProbe(testCase) ? probeTestSerializer : maskingSerializer).toJson(testCase);
    }

    public String headersJson(CatsTestCase testCase) {
        return (SyntheticAuthHeaderPolicy.isProbe(testCase) ? probeRequestSerializer : maskingSerializer)
                .toJson(testCase.getRequest().getHeaders());
    }

    public String curl(CatsTestCase testCase) {
        return headers.curl(testCase);
    }

    private static Gson maskingSerializer(ReportingArguments arguments) {
        return new GsonBuilder().setStrictness(Strictness.LENIENT).setPrettyPrinting().disableHtmlEscaping()
                .setExclusionStrategies(new ExcludeTestCaseStrategy())
                .registerTypeAdapter(Long.class, new LongTypeSerializer())
                .registerTypeAdapter(KeyValuePair.class, new KeyValueSerializer(arguments)).serializeNulls().create();
    }
}
