package com.endava.cats.generator.format.api;

import java.util.List;

public interface OpenAPIFormat {

    /**
     * Provides a list of OpenAPI formats supported by a {@code DataFormatGenerator}
     *
     * @return a list of OpenAPI formats
     */
    List<String> marchingFormats();
}
