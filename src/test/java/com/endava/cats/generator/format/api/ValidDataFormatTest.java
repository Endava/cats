package com.endava.cats.generator.format.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ValidDataFormatTest {
    @Inject
    ValidDataFormat validDataFormat;

    @Inject
    Instance<OpenAPIFormat> openAPIFormats;


    @ParameterizedTest
    @CsvSource({"city,true", "CITY,true", "cityName,true", "city_name,true", "other#city,true",
            "iban,true", "IBAN,true", "other#iban,true",
            "bic,true", "BIC,true", "swift,true", "bic_code,true", "other#swift_code,true", "other#bank_identifier_code,true", "other#bank_identifier,true", "other#bank_code,true"
    })
    void shouldReturnOneGeneratorWithProperty(String propertyName) {
        List<ValidDataFormatGenerator> validDataFormatList = validDataFormat.getGenerators(new Schema<>(), propertyName);
        Assertions.assertThat(validDataFormatList).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "city", "iban", "swift", "bic",
            "isin", "iso6166", "lei", "iso17442", "aba", "routing-number", "routingNumber", "rtn",
            "mcc", "iso18245", "merchant-category-code", "ulid", "jwt", "jws", "base64url",
            "sha256", "sha-256", "arn", "aws-arn", "imei", "orcid", "doi",
            "char", "commonmark", "markdown", "date-time-local", "local-date-time",
            "decimal", "decimal128", "html", "http-date", "ipv4-cidr", "ipv6-cidr",
            "media-range", "time-local", "local-time", "e164", "language", "byte", "binary",
            "sf-binary", "sf-boolean", "sf-string", "sf-token"
    })
    void shouldReturnOneGeneratorWithFormat(String format) {
        Schema<?> schema = new Schema<>();
        schema.setFormat(format);
        List<ValidDataFormatGenerator> validDataFormatList = validDataFormat.getGenerators(schema, "");
        Assertions.assertThat(validDataFormatList).hasSize(1);
        Assertions.assertThat(validDataFormat.generate(schema, "")).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "isin,IsinGenerator", "security#isinCode,IsinGenerator",
            "lei,LeiGenerator", "entity#legalEntityIdentifier,LeiGenerator",
            "routingNumber,RoutingNumberGenerator", "bank#aba_routing_number,RoutingNumberGenerator", "transitNumber,RoutingNumberGenerator",
            "mcc,MerchantCategoryCodeGenerator", "merchant#merchantCategoryCode,MerchantCategoryCodeGenerator",
            "ulid,UlidGenerator", "event#requestUlid,UlidGenerator",
            "jwt,JwtGenerator", "auth#idToken,JwtGenerator", "accessToken,JwtGenerator", "refresh_token,JwtGenerator",
            "sha256,Sha256Generator", "file#sha256Hash,Sha256Generator", "sha256Checksum,Sha256Generator",
            "arn,AwsArnGenerator", "iam#roleArn,AwsArnGenerator", "topicArn,AwsArnGenerator",
            "imei,ImeiGenerator", "device#imeiNumber,ImeiGenerator",
            "orcid,OrcidGenerator", "author#orcidId,OrcidGenerator",
            "doi,DoiGenerator", "paper#digitalObjectIdentifier,DoiGenerator",
            "markdown,CommonMarkGenerator", "doc#commonmark,CommonMarkGenerator",
            "localDateTime,DateTimeLocalGenerator", "event#date_time_local,DateTimeLocalGenerator",
            "localTime,TimeLocalGenerator", "schedule#time_local,TimeLocalGenerator",
            "httpDate,HttpDateGenerator", "header#http_date,HttpDateGenerator",
            "ipv6Cidr,IPV6CidrGenerator", "network#ipv6Subnet,IPV6CidrGenerator",
            "networkCidr,IPAddressRangeGenerator", "arrivalTime,TimeOfDayGenerator"
    })
    void shouldResolveUniqueGeneratorForNewProperties(String propertyName, String expectedSimpleName) {
        List<ValidDataFormatGenerator> generators = validDataFormat.getGenerators(new Schema<>(), propertyName);
        Assertions.assertThat(generators)
                .hasSize(1)
                .first()
                .extracting(g -> g.getClass().getSimpleName())
                .isEqualTo(expectedSimpleName);
    }

    @ParameterizedTest
    @CsvSource({
            "date-time-local,createdAt,DateTimeLocalGenerator",
            "date-time-local,arrivalTime,DateTimeLocalGenerator",
            "date-time-local,localDateTime,DateTimeLocalGenerator",
            "time-local,arrivalTime,TimeLocalGenerator",
            "time-local,localTime,TimeLocalGenerator",
            "time-local,createdAt,TimeLocalGenerator",
            "http-date,modifiedAt,HttpDateGenerator",
            "http-date,httpDate,HttpDateGenerator",
            "unixtime,createdAt,UnixtimeGenerator",
            "unixtime,unixTime,UnixtimeGenerator",
            "date-time,createdAt,DateTimeGenerator",
            "ipv6-cidr,ipv6Cidr,IPV6CidrGenerator",
            "ipv6-cidr,networkCidr,IPV6CidrGenerator",
            "ipv6-cidr,serverIpv6,IPV6CidrGenerator",
            "ipv4-cidr,clientIp,IPAddressRangeGenerator",
            "cidr,clientIp,IPAddressRangeGenerator",
            "base64url,base64Url,Base64UrlGenerator",
            "uri-reference,avatarUri,URIReferenceGenerator",
            "uri-template,endpointUrl,URITemplateGenerator",
            "iri,profileUrl,IRIGenerator",
            "iri-reference,resourceLink,IRIReferenceGenerator",
            "idn-email,userEmail,IdnEmailGenerator",
            "e164,phoneNumber,PhoneNumberGenerator",
            "media-range,contentType,ContentTypeGenerator",
            "html,description,HtmlGenerator",
            "html,body,HtmlGenerator",
            "commonmark,description,CommonMarkGenerator",
            "markdown,body,CommonMarkGenerator"
    })
    void shouldNotClashOnCombinedFormatAndPropertyName(String format, String propertyName, String expectedSimpleName) {
        Schema<?> schema = new Schema<>();
        schema.setFormat(format);
        List<ValidDataFormatGenerator> generators = validDataFormat.getGenerators(schema, propertyName);
        Assertions.assertThat(generators)
                .hasSize(1)
                .first()
                .extracting(g -> g.getClass().getSimpleName())
                .isEqualTo(expectedSimpleName);
    }

    @Test
    void shouldNeverClashAcrossAllDeclaredOpenAPIFormats() {
        List<String> declaredFormats = openAPIFormats.stream()
                .flatMap(openAPIFormat -> openAPIFormat.matchingFormats().stream())
                .filter(format -> format != null && !format.isBlank())
                .distinct()
                .toList();

        Assertions.assertThat(declaredFormats).isNotEmpty();
        for (String format : declaredFormats) {
            Schema<?> schema = new Schema<>();
            schema.setFormat(format);
            List<ValidDataFormatGenerator> matches = validDataFormat.getGenerators(schema, "");
            Assertions.assertThat(matches)
                    .as("Format '%s' matched multiple generators: %s", format, matches)
                    .hasSizeLessThanOrEqualTo(1);
        }
    }

    @Test
    void shouldUseSubstringMatchingForSchemaPatternAndReturnNullWhenPatternDoesNotMatch() {
        Schema<String> matchingUnanchoredSchema = new Schema<>();
        matchingUnanchoredSchema.setPattern("[a-z]+");
        Assertions.assertThat(validDataFormat.generate(matchingUnanchoredSchema, "password")).isNotNull();

        Schema<String> nonMatchingSchema = new Schema<>();
        nonMatchingSchema.setPattern("^[0-9]+$");
        Assertions.assertThat(validDataFormat.generate(nonMatchingSchema, "password")).isNull();
    }
}
