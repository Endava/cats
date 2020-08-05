package com.endava.cats.model.factory;

import com.endava.cats.CatsMain;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.UrlParams;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class FuzzingDataFactoryTest {

    @MockBean
    private CatsUtil catsUtil;
    @MockBean
    private UrlParams urlParams;

    private FuzzingDataFactory fuzzingDataFactory;

    @BeforeEach
    void setup() {
        fuzzingDataFactory = new FuzzingDataFactory(catsUtil, urlParams);
    }

    @Test
    void givenAContract_whenParsingThePathItemDetailsForPost_thenCorrectFuzzingDataAreBeingReturned() throws Exception {
        OpenAPIParser openAPIV3Parser = new OpenAPIParser();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        OpenAPI openAPI = openAPIV3Parser.readContents(new String(Files.readAllBytes(Paths.get("src/test/resources/petstore.yml"))), null, options).getOpenAPI();
        Map<String, Schema> schemas = CatsMain.getSchemas(openAPI);
        PathItem pathItem = openAPI.getPaths().get("/pets");
        List<FuzzingData> data = fuzzingDataFactory.fromPathItem("/pets", pathItem, schemas);
        Mockito.doCallRealMethod().when(catsUtil).getDefinitionNameFromRef(Mockito.anyString());

        Assertions.assertThat(data).hasSize(3);
        Assertions.assertThat(data.get(0).getMethod()).isEqualByComparingTo(HttpMethod.POST);
        Assertions.assertThat(data.get(1).getMethod()).isEqualByComparingTo(HttpMethod.POST);
        Assertions.assertThat(data.get(2).getMethod()).isEqualByComparingTo(HttpMethod.GET);
    }
}
