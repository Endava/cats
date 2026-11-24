package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Fuzzer that sends multipart requests in which a file part is empty, one file at a time.
 */
@Singleton
@HttpFuzzer
public class MultipartEmptyFileFuzzer extends BaseMultipartFuzzer {

    /**
     * Creates a new MultipartEmptyFileFuzzer instance.
     *
     * @param executor the executor
     */
    @Inject
    public MultipartEmptyFileFuzzer(SimpleExecutor executor) {
        super(executor);
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        return filePartNames(data).stream()
                .map(name -> new MultipartCase("Send a multipart request with an empty [" + name + "] file",
                        replaceFilePart(name, part -> new MultipartEncoder.Part(part.name(), part.fileName(),
                                part.contentType(), new byte[0]))))
                .toList();
    }

    @Override
    public String description() {
        return "send a multipart request with each file part empty";
    }
}
