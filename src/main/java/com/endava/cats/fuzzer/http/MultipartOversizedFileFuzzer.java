package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.io.util.MultipartEncoder;
import com.endava.cats.model.FuzzingData;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * Fuzzer that sends multipart requests in which a file part is larger than the configured size, one file at a time.
 * The size is read from the {@code MultipartOversizedFile.sizeBytes} fuzzers configuration property.
 */
@Singleton
@HttpFuzzer
public class MultipartOversizedFileFuzzer extends BaseMultipartFuzzer {
    static final String SIZE_PROPERTY = "MultipartOversizedFile.sizeBytes";
    static final int DEFAULT_SIZE_BYTES = 10 * 1024 * 1024 + 1;

    private final CatsGlobalContext globalContext;

    /**
     * Creates a new MultipartOversizedFileFuzzer instance.
     *
     * @param executor      the executor
     * @param globalContext the global context holding the fuzzers configuration
     */
    @Inject
    public MultipartOversizedFileFuzzer(SimpleExecutor executor, CatsGlobalContext globalContext) {
        super(executor);
        this.globalContext = globalContext;
    }

    @Override
    protected List<MultipartCase> cases(FuzzingData data) {
        int size = configuredSize();
        return filePartNames(data).stream()
                .map(name -> new MultipartCase("Send a multipart request with the [" + name + "] file of [" + size + "] bytes",
                        replaceFilePart(name, part -> new MultipartEncoder.Part(part.name(), part.fileName(),
                                part.contentType(), Arrays.copyOf(part.bytes(), size)))))
                .toList();
    }

    private int configuredSize() {
        String configured = globalContext.getFuzzersConfigurationProperty(SIZE_PROPERTY);
        if (configured == null) {
            return DEFAULT_SIZE_BYTES;
        }
        try {
            int size = Integer.parseInt(configured.trim());
            if (size > 0) {
                return size;
            }
        } catch (NumberFormatException _) {
            //handled below
        }
        logger().warn("Invalid {} value [{}]. Using the default of {} bytes", SIZE_PROPERTY, configured, DEFAULT_SIZE_BYTES);
        return DEFAULT_SIZE_BYTES;
    }

    @Override
    public String description() {
        return "send a multipart request with each file part above the configured size ("
                + SIZE_PROPERTY + ", default " + DEFAULT_SIZE_BYTES + " bytes)";
    }
}
