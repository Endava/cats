package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.headers.base.BaseSecurityChecksHeadersFuzzer;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
@HeaderFuzzer
public class AcceptLanguageHeadersFuzzer extends BaseSecurityChecksHeadersFuzzer {

    static final List<String> ACCEPT_LANGUAGE_VALUES = List.of("fr", "zh-CN", "ar-SA", "en-GB", "tlh-KL");

    public AcceptLanguageHeadersFuzzer(SimpleExecutor simpleExecutor) {
        super(simpleExecutor);
    }

    @Override
    public List<Set<CatsHeader>> getHeaders(FuzzingData data) {
        List<Set<CatsHeader>> headersToSend = new ArrayList<>();

        for (String value : ACCEPT_LANGUAGE_VALUES) {
            Set<CatsHeader> headers = new HashSet<>(data.getHeaders());
            headers.add(CatsHeader.builder().name("Accept-Language").value(value).build());
            headersToSend.add(headers);
        }

        return headersToSend;
    }

    @Override
    public ResponseCodeFamily getResponseCodeFamily() {
        return ResponseCodeFamilyPredefined.TWOXX;
    }

    @Override
    public String getExpectedResponseCode() {
        return "200";
    }

    @Override
    public String typeOfHeader() {
        return "locale";
    }

    @Override
    public String targetHeaderName() {
        return "Accept-Language";
    }

}
