package com.endava.cats;

import com.endava.cats.aop.DryRunEntry;
import com.endava.cats.args.util.ProfileLoader;
import com.endava.cats.command.AnalyzeCommand;
import com.endava.cats.command.InfoCommand;
import com.endava.cats.command.InitCommand;
import com.endava.cats.command.SmokeCommand;
import com.endava.cats.command.StatsCommand;
import com.endava.cats.command.TriageCommand;
import com.endava.cats.command.model.FuzzerListEntry;
import com.endava.cats.command.model.MutatorEntry;
import com.endava.cats.command.model.PathDetailsEntry;
import com.endava.cats.command.model.PathListEntry;
import com.endava.cats.command.model.ValidContractEntry;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.ProcessingError;
import com.endava.cats.model.RequestProvenance;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.model.RunOutcome;
import com.endava.cats.model.TimeExecution;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.report.BaselineComparisonEngine;
import com.endava.cats.report.SummaryReportContext;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.KeyValuePair;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.internal.function.PassthruPathFunction;
import com.jayway.jsonpath.internal.function.json.Append;
import com.jayway.jsonpath.internal.function.json.KeySetFunction;
import com.jayway.jsonpath.internal.function.numeric.Average;
import com.jayway.jsonpath.internal.function.numeric.Max;
import com.jayway.jsonpath.internal.function.numeric.Min;
import com.jayway.jsonpath.internal.function.numeric.StandardDeviation;
import com.jayway.jsonpath.internal.function.numeric.Sum;
import com.jayway.jsonpath.internal.function.text.Concatenate;
import com.jayway.jsonpath.internal.function.text.Length;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.swagger.parser.Swagger20Parser;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.core.jackson.ApiResponsesSerializer;
import io.swagger.v3.core.jackson.ExampleSerializer;
import io.swagger.v3.core.jackson.MediaTypeSerializer;
import io.swagger.v3.core.jackson.PathsSerializer;
import io.swagger.v3.core.jackson.Schema31Serializer;
import io.swagger.v3.core.jackson.SchemaSerializer;
import io.swagger.v3.core.jackson.mixin.Components31Mixin;
import io.swagger.v3.core.jackson.mixin.ComponentsMixin;
import io.swagger.v3.core.jackson.mixin.DateSchemaMixin;
import io.swagger.v3.core.jackson.mixin.Discriminator31Mixin;
import io.swagger.v3.core.jackson.mixin.ExtensionsMixin;
import io.swagger.v3.core.jackson.mixin.Info31Mixin;
import io.swagger.v3.core.jackson.mixin.OpenAPI31Mixin;
import io.swagger.v3.core.jackson.mixin.OpenAPIMixin;
import io.swagger.v3.core.jackson.mixin.OperationMixin;
import io.swagger.v3.core.jackson.mixin.Schema31Mixin;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.annotations.OpenAPI30;
import io.swagger.v3.oas.models.annotations.OpenAPI31;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.links.LinkParameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ByteArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.EmailSchema;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.media.EncodingProperty;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.PasswordSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.media.XML;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariable;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.reference.OpenAPI31Traverser;
import io.swagger.v3.parser.reference.OpenAPIDereferencer;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Classes which must be registered for reflection in order to GraalVM to include them in the final image.
 */
@RegisterForReflection(targets = {
        DryRunEntry.class, CatsTestCaseSummary.class,
        OpenAPI.class, Components.class, ExternalDocumentation.class, Operation.class, PathItem.class,
        Paths.class, Callback.class, Example.class, Header.class, Contact.class,
        Info.class, License.class, Link.class, LinkParameter.class, ArraySchema.class, BinarySchema.class,
        BooleanSchema.class, ByteArraySchema.class, ComposedSchema.class, Content.class, DateSchema.class,
        DateTimeSchema.class, Discriminator.class, EmailSchema.class, Encoding.class, EncodingProperty.class,
        FileSchema.class, IntegerSchema.class, JsonSchema.class, MapSchema.class, MediaType.class, NumberSchema.class, ObjectSchema.class,
        PasswordSchema.class, Schema.class, StringSchema.class, UUIDSchema.class, XML.class,
        CookieParameter.class, HeaderParameter.class, Parameter.class, PathParameter.class, QueryParameter.class,
        RequestBody.class, ApiResponse.class, ApiResponses.class, OAuthFlow.class, OAuthFlows.class, Scopes.class, SecurityRequirement.class,
        SecurityScheme.class, Server.class, ServerVariable.class, ServerVariables.class, Tag.class,
        PassthruPathFunction.class, Append.class, KeySetFunction.class,
        Average.class, Max.class, Min.class, StandardDeviation.class, Sum.class,
        Concatenate.class, Length.class,
        GsonJsonProvider.class, JacksonJsonNodeJsonProvider.class, JacksonJsonProvider.class,
        JsonSmartJsonProvider.class, GsonMappingProvider.class, JacksonMappingProvider.class, JsonSmartMappingProvider.class,
        com.webfuzzing.commons.auth.Auth.class, com.webfuzzing.commons.auth.AuthenticationInfo.class, com.webfuzzing.commons.auth.Configs.class,
        com.webfuzzing.commons.auth.Header.class, com.webfuzzing.commons.auth.LoginEndpoint.class, com.webfuzzing.commons.auth.LoginEndpoint.HttpVerb.class,
        com.webfuzzing.commons.auth.PayloadUsernamePassword.class, com.webfuzzing.commons.auth.TokenHandling.class,
        com.webfuzzing.commons.auth.TokenHandling.ExtractFrom.class, com.webfuzzing.commons.auth.TokenHandling.SendIn.class,
        com.webfuzzing.commons.faults.DefinedFaultCategory.class, com.webfuzzing.commons.faults.FaultCategory.class,
        com.webfuzzing.commons.report.Coverage.class, com.webfuzzing.commons.report.CoverageCriterion.class,
        com.webfuzzing.commons.report.CoveredEndpoint.class, com.webfuzzing.commons.report.FaultCategoryId.class,
        com.webfuzzing.commons.report.Faults.class, com.webfuzzing.commons.report.FoundFault.class,
        com.webfuzzing.commons.report.ProblemDetails.class, com.webfuzzing.commons.report.RESTReport.class,
        com.webfuzzing.commons.report.Report.class, com.webfuzzing.commons.report.TestCase.class,
        com.webfuzzing.commons.report.Warning.class,
        TriageCommand.TriageReport.class, TriageCommand.RootCauseCluster.class,
        TriageCommand.SynthesizedCorrelationRule.class,
        AnalyzeCommand.BlueprintReport.class, AnalyzeCommand.ScoreBreakdown.class,
        AnalyzeCommand.ProducerConsumerChain.class,
        SmokeCommand.SmokeReport.class, SmokeCommand.OperationProbe.class,
        InitCommand.InitResult.class, InitCommand.AgentPlatform.class,
        BaselineComparisonEngine.ComparisonResult.class, BaselineComparisonEngine.FindingDelta.class,
        ProfileLoader.Profile.class,
        CatsTestReport.JunitTestSuite.class,
        CatsDSLParser.class, CatsRandom.class,
        Base64.Encoder.class, Base64.Decoder.class, Base64.class, Locale.class, BigDecimal.class, BigInteger.class,
        StringUtils.class, RandomStringUtils.class,
        LocalDate.class, LocalDateTime.class, OffsetDateTime.class, OffsetTime.class, ZonedDateTime.class,
        Instant.class, Duration.class, Period.class, ZoneOffset.class, DateTimeFormatter.class, LocalTime.class, ZoneId.class, ChronoUnit.class,
        String.class, UUID.class, net.minidev.asm.ConvertDate.class, net.minidev.asm.DefaultConverter.class,
        SwaggerParserExtension.class, OpenAPIV3Parser.class, AuthorizationValue.class, SwaggerParseResult.class, ParseOptions.class,
        ApiResponsesSerializer.class, ExampleSerializer.class, MediaTypeSerializer.class, PathsSerializer.class, SchemaSerializer.class, Schema31Serializer.class,
        ComponentsMixin.class, DateSchemaMixin.class, ExtensionsMixin.class, OpenAPIMixin.class, OperationMixin.class,
        Components31Mixin.class, Discriminator31Mixin.class, Info31Mixin.class, OpenAPI31Mixin.class, Schema31Mixin.class,
        OpenAPI30.class, OpenAPI31.class, OpenAPI31Traverser.class, OpenAPIDereferencer.class,
        Swagger20Parser.class, SwaggerParser.class, SwaggerConverter.class, io.swagger.parser.SwaggerParserExtension.class,
        io.swagger.jackson.mixin.IgnoreOriginalRefMixin.class, io.swagger.jackson.mixin.OriginalRefMixin.class, io.swagger.jackson.mixin.ResponseSchemaMixin.class,
        io.swagger.models.AbstractModel.class, io.swagger.models.ArrayModel.class, io.swagger.models.ComposedModel.class,
        io.swagger.models.Contact.class, io.swagger.models.ExternalDocs.class, io.swagger.models.HttpMethod.class,
        io.swagger.models.Info.class, io.swagger.models.License.class, io.swagger.models.Model.class, io.swagger.models.ModelImpl.class,
        io.swagger.models.Operation.class, io.swagger.models.Path.class, io.swagger.models.RefModel.class, io.swagger.models.RefPath.class,
        io.swagger.models.RefResponse.class, io.swagger.models.Response.class, io.swagger.models.Scheme.class,
        io.swagger.models.SecurityRequirement.class, io.swagger.models.SecurityScope.class, io.swagger.models.Swagger.class,
        io.swagger.models.Tag.class, io.swagger.models.Xml.class,
        io.swagger.models.auth.AbstractSecuritySchemeDefinition.class, io.swagger.models.auth.ApiKeyAuthDefinition.class,
        io.swagger.models.auth.AuthorizationValue.class, io.swagger.models.auth.BasicAuthDefinition.class, io.swagger.models.auth.In.class,
        io.swagger.models.auth.OAuth2Definition.class, io.swagger.models.auth.SecuritySchemeDefinition.class,
        io.swagger.models.parameters.AbstractParameter.class, io.swagger.models.parameters.AbstractSerializableParameter.class,
        io.swagger.models.parameters.BodyParameter.class, io.swagger.models.parameters.CookieParameter.class,
        io.swagger.models.parameters.FormParameter.class, io.swagger.models.parameters.HeaderParameter.class,
        io.swagger.models.parameters.Parameter.class, io.swagger.models.parameters.PathParameter.class,
        io.swagger.models.parameters.QueryParameter.class, io.swagger.models.parameters.RefParameter.class,
        io.swagger.models.parameters.SerializableParameter.class,
        io.swagger.models.properties.AbstractNumericProperty.class, io.swagger.models.properties.AbstractProperty.class,
        io.swagger.models.properties.ArrayProperty.class, io.swagger.models.properties.BaseIntegerProperty.class,
        io.swagger.models.properties.BinaryProperty.class, io.swagger.models.properties.BooleanProperty.class,
        io.swagger.models.properties.ByteArrayProperty.class, io.swagger.models.properties.ComposedProperty.class,
        io.swagger.models.properties.DateProperty.class, io.swagger.models.properties.DateTimeProperty.class,
        io.swagger.models.properties.DecimalProperty.class, io.swagger.models.properties.DoubleProperty.class,
        io.swagger.models.properties.EmailProperty.class, io.swagger.models.properties.FileProperty.class,
        io.swagger.models.properties.FloatProperty.class, io.swagger.models.properties.IntegerProperty.class,
        io.swagger.models.properties.LongProperty.class, io.swagger.models.properties.MapProperty.class,
        io.swagger.models.properties.ObjectProperty.class, io.swagger.models.properties.PasswordProperty.class,
        io.swagger.models.properties.Property.class, io.swagger.models.properties.RefProperty.class,
        io.swagger.models.properties.StringProperty.class, io.swagger.models.properties.UUIDProperty.class,
        io.swagger.models.properties.UntypedProperty.class,
        io.swagger.models.refs.GenericRef.class, io.swagger.models.refs.RefFormat.class, io.swagger.models.refs.RefType.class,
        FuzzerListEntry.class, FuzzerListEntry.FuzzerDetails.class, TimeExecutionDetails.class, TimeExecution.class,
        CatsTestReport.class, CatsResultFactory.class, CatsResultFactory.CatsResult.class, CatsTestCase.class,
        KeyValuePair.class, CatsResponse.class, CatsRequest.class, ResourceCorrelation.class, RequestProvenance.class,
        RequestTarget.class, RequestTarget.Location.class, ExecutionSummary.class, RunOutcome.class, RunOutcome.Status.class,
        ProcessingError.class, SummaryReportContext.class, SummaryReportContext.StopLimit.class,
        SummaryReportContext.ProcessingErrorEntry.class, SummaryReportContext.ResponseCodeEntry.class,
        SummaryReportContext.FailingPathEntry.class, PathListEntry.class, PathListEntry.PathDetails.class,
        InfoCommand.CatsInfo.class, StatsCommand.Stats.class, PathDetailsEntry.class, PathDetailsEntry.OperationDetails.class,
        ValidContractEntry.class, MutatorEntry.class, JavaTimeModule.class
})
public class ReflectionConfig {
    @TargetClass(tools.jackson.databind.ext.OptionalHandlerFactory.class)
    static final class Target_tools_jackson_databind_ext_OptionalHandlerFactory {
        @Substitute
        public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type) {
            return null;
        }

        @Substitute
        public ValueDeserializer<?> findDeserializer(DeserializationConfig config, JavaType type) {
            return null;
        }

        @Substitute
        public boolean hasDeserializerFor(Class<?> valueType) {
            return false;
        }
    }
}
