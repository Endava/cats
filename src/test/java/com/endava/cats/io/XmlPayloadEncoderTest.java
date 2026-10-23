package com.endava.cats.io;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.XML;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

@QuarkusTest
class XmlPayloadEncoderTest {

    @ParameterizedTest
    @CsvSource({
            "application/xml, true",
            "text/xml, true",
            "application/problem+xml, true",
            "application/xml; charset=utf-8, true",
            "application/json, false",
            "'', false"
    })
    void shouldDetectXmlContentType(String contentType, boolean expected) {
        Assertions.assertThat(XmlPayloadEncoder.isXmlContentType(contentType)).isEqualTo(expected);
    }

    @Test
    void shouldEncodeJsonObjectWithXmlAttributesNestedObjectsAndWrappedArrays() {
        ObjectSchema actorSchema = new ObjectSchema();
        actorSchema.setXml(new XML().name("actor"));
        actorSchema.addProperty("billing", new IntegerSchema().xml(new XML().name("billing").attribute(true)));
        actorSchema.addProperty("name", new StringSchema());

        ObjectSchema directorSchema = new ObjectSchema();
        directorSchema.setXml(new XML().name("director"));
        directorSchema.addProperty("nationality", new StringSchema().xml(new XML().name("nationality").attribute(true)));
        directorSchema.addProperty("name", new StringSchema());

        ArraySchema castSchema = new ArraySchema();
        castSchema.setXml(new XML().name("cast").wrapped(true));
        castSchema.setItems(new Schema<>().$ref("#/components/schemas/ActorDto"));

        ObjectSchema movieSchema = new ObjectSchema();
        movieSchema.setXml(new XML().name("movie"));
        movieSchema.addProperty("id", new IntegerSchema().xml(new XML().name("id").attribute(true)));
        movieSchema.addProperty("year", new IntegerSchema().xml(new XML().name("year").attribute(true)));
        movieSchema.addProperty("title", new StringSchema());
        movieSchema.addProperty("genre", new StringSchema());
        movieSchema.addProperty("director", new Schema<>().$ref("#/components/schemas/DirectorDto"));
        movieSchema.addProperty("cast", castSchema);

        Map<String, Schema> schemaMap = Map.of(
                "ActorDto", actorSchema,
                "DirectorDto", directorSchema,
                "MovieDto", movieSchema);

        String payload = """
                {
                  "year": 1999,
                  "title": "The Matrix & Reloaded",
                  "genre": "SCIFI",
                  "director": {"nationality": "US", "name": "Wachowski"},
                  "cast": [{"billing": 1, "name": "Keanu Reeves"}]
                }
                """;

        String xml = XmlPayloadEncoder.encode(payload, new Schema<>().$ref("#/components/schemas/MovieDto"), schemaMap);

        Assertions.assertThat(xml).isEqualTo(
                "<movie year=\"1999\">" +
                        "<title>The Matrix &amp; Reloaded</title>" +
                        "<genre>SCIFI</genre>" +
                        "<director nationality=\"US\"><name>Wachowski</name></director>" +
                        "<cast><actor billing=\"1\"><name>Keanu Reeves</name></actor></cast>" +
                        "</movie>");
    }

    @Test
    void shouldReturnRawPayloadWhenInvalidJsonOrEmptyObject() {
        ObjectSchema emptySchema = new ObjectSchema();
        emptySchema.setXml(new XML().name("movie"));
        Assertions.assertThat(XmlPayloadEncoder.encode("{}", emptySchema, Map.of())).isEqualTo("<movie/>");
        Assertions.assertThat(XmlPayloadEncoder.encode("not-json", emptySchema, Map.of())).isEqualTo("not-json");
        Assertions.assertThat(XmlPayloadEncoder.encode("", emptySchema, Map.of())).isEmpty();
    }
}
