package com.endava.cats;

import com.endava.cats.aop.DryRunEntry;
import com.endava.cats.command.InfoCommand;
import com.endava.cats.command.StatsCommand;
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
import com.endava.cats.model.TimeExecution;
import com.endava.cats.model.TimeExecutionDetails;
import com.endava.cats.util.KeyValuePair;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.curiousoddman.rgxgen.RgxGen;
import com.github.curiousoddman.rgxgen.config.RgxGenOption;
import com.github.curiousoddman.rgxgen.config.RgxGenProperties;
import com.github.curiousoddman.rgxgen.iterators.ArrayIterator;
import com.github.curiousoddman.rgxgen.iterators.CaseVariationIterator;
import com.github.curiousoddman.rgxgen.iterators.ChoiceIterator;
import com.github.curiousoddman.rgxgen.iterators.IncrementalLengthIterator;
import com.github.curiousoddman.rgxgen.iterators.NegativeStringIterator;
import com.github.curiousoddman.rgxgen.iterators.PermutationsIterator;
import com.github.curiousoddman.rgxgen.iterators.ReferenceIterator;
import com.github.curiousoddman.rgxgen.iterators.SingleValueIterator;
import com.github.curiousoddman.rgxgen.iterators.StringIterator;
import com.github.curiousoddman.rgxgen.iterators.suppliers.ArrayIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.ChoiceIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.GroupIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.IncrementalLengthIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.NegativeIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.PermutationsIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.ReferenceIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.SingleCaseInsensitiveValueIteratorSupplier;
import com.github.curiousoddman.rgxgen.iterators.suppliers.SingleValueIteratorSupplier;
import com.github.curiousoddman.rgxgen.nodes.Choice;
import com.github.curiousoddman.rgxgen.nodes.FinalSymbol;
import com.github.curiousoddman.rgxgen.nodes.Group;
import com.github.curiousoddman.rgxgen.nodes.GroupRef;
import com.github.curiousoddman.rgxgen.nodes.Node;
import com.github.curiousoddman.rgxgen.nodes.NotSymbol;
import com.github.curiousoddman.rgxgen.nodes.Repeat;
import com.github.curiousoddman.rgxgen.nodes.Sequence;
import com.github.curiousoddman.rgxgen.nodes.SymbolSet;
import com.github.curiousoddman.rgxgen.parsing.NodeTreeBuilder;
import com.github.curiousoddman.rgxgen.parsing.dflt.CharIterator;
import com.github.curiousoddman.rgxgen.parsing.dflt.DefaultTreeBuilder;
import com.github.curiousoddman.rgxgen.util.Util;
import com.github.curiousoddman.rgxgen.visitors.GenerationVisitor;
import com.github.curiousoddman.rgxgen.visitors.GenerationVisitorBuilder;
import com.github.curiousoddman.rgxgen.visitors.GenerationVisitorCaseInsensitive;
import com.github.curiousoddman.rgxgen.visitors.NodeVisitor;
import com.github.curiousoddman.rgxgen.visitors.NotMatchingCaseInsensitiveGenerationVisitor;
import com.github.curiousoddman.rgxgen.visitors.NotMatchingGenerationVisitor;
import com.github.curiousoddman.rgxgen.visitors.UniqueGenerationVisitor;
import com.github.curiousoddman.rgxgen.visitors.UniqueValuesCountingVisitor;
import com.github.javafaker.Address;
import com.github.javafaker.Name;
import com.github.javafaker.service.FakeValues;
import com.github.javafaker.service.FakeValuesGrouping;
import com.github.javafaker.service.FakeValuesService;
import com.github.javafaker.service.RandomService;
import com.github.javafaker.service.files.EnFile;
import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.GsonBuildConfig;
import com.google.gson.internal.JavaVersion;
import com.google.gson.internal.JsonReaderInternalAccess;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.PreJava9DateFormatProvider;
import com.google.gson.internal.Primitives;
import com.google.gson.internal.Streams;
import com.google.gson.internal.UnsafeAllocator;
import com.google.gson.internal.bind.ArrayTypeAdapter;
import com.google.gson.internal.bind.CollectionTypeAdapterFactory;
import com.google.gson.internal.bind.DefaultDateTypeAdapter;
import com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory;
import com.google.gson.internal.bind.JsonTreeReader;
import com.google.gson.internal.bind.JsonTreeWriter;
import com.google.gson.internal.bind.MapTypeAdapterFactory;
import com.google.gson.internal.bind.NumberTypeAdapter;
import com.google.gson.internal.bind.ObjectTypeAdapter;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;
import com.google.gson.internal.bind.TreeTypeAdapter;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.internal.bind.util.ISO8601Utils;
import com.google.gson.internal.sql.SqlTypesSupport;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.MalformedJsonException;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.internal.CharacterIndex;
import com.jayway.jsonpath.internal.DefaultsImpl;
import com.jayway.jsonpath.internal.EvaluationAbortException;
import com.jayway.jsonpath.internal.EvaluationContext;
import com.jayway.jsonpath.internal.JsonContext;
import com.jayway.jsonpath.internal.JsonFormatter;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.internal.Path;
import com.jayway.jsonpath.internal.PathRef;
import com.jayway.jsonpath.internal.Utils;
import com.jayway.jsonpath.internal.filter.Evaluator;
import com.jayway.jsonpath.internal.filter.EvaluatorFactory;
import com.jayway.jsonpath.internal.filter.ExpressionNode;
import com.jayway.jsonpath.internal.filter.FilterCompiler;
import com.jayway.jsonpath.internal.filter.LogicalExpressionNode;
import com.jayway.jsonpath.internal.filter.LogicalOperator;
import com.jayway.jsonpath.internal.filter.PatternFlag;
import com.jayway.jsonpath.internal.filter.RelationalExpressionNode;
import com.jayway.jsonpath.internal.filter.RelationalOperator;
import com.jayway.jsonpath.internal.filter.ValueNode;
import com.jayway.jsonpath.internal.filter.ValueNodes;
import com.jayway.jsonpath.internal.function.ParamType;
import com.jayway.jsonpath.internal.function.PassthruPathFunction;
import com.jayway.jsonpath.internal.function.PathFunction;
import com.jayway.jsonpath.internal.function.PathFunctionFactory;
import com.jayway.jsonpath.internal.function.json.Append;
import com.jayway.jsonpath.internal.function.json.KeySetFunction;
import com.jayway.jsonpath.internal.function.latebinding.ILateBindingValue;
import com.jayway.jsonpath.internal.function.latebinding.JsonLateBindingValue;
import com.jayway.jsonpath.internal.function.latebinding.PathLateBindingValue;
import com.jayway.jsonpath.internal.function.numeric.AbstractAggregation;
import com.jayway.jsonpath.internal.function.numeric.Average;
import com.jayway.jsonpath.internal.function.numeric.Max;
import com.jayway.jsonpath.internal.function.numeric.Min;
import com.jayway.jsonpath.internal.function.numeric.StandardDeviation;
import com.jayway.jsonpath.internal.function.numeric.Sum;
import com.jayway.jsonpath.internal.function.text.Concatenate;
import com.jayway.jsonpath.internal.function.text.Length;
import com.jayway.jsonpath.internal.path.*;
import com.jayway.jsonpath.spi.cache.Cache;
import com.jayway.jsonpath.spi.cache.CacheProvider;
import com.jayway.jsonpath.spi.cache.LRUCache;
import com.jayway.jsonpath.spi.cache.NOOPCache;
import com.jayway.jsonpath.spi.json.AbstractJsonProvider;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JettisonProvider;
import com.jayway.jsonpath.spi.json.JsonOrgJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JsonOrgMappingProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingException;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.swagger.parser.Swagger20Parser;
import io.swagger.parser.SwaggerParser;
import io.swagger.parser.SwaggerResolver;
import io.swagger.v3.core.filter.OpenAPI31SpecFilter;
import io.swagger.v3.core.jackson.Schema31Serializer;
import io.swagger.v3.core.jackson.mixin.Components31Mixin;
import io.swagger.v3.core.jackson.mixin.Discriminator31Mixin;
import io.swagger.v3.core.jackson.mixin.Info31Mixin;
import io.swagger.v3.core.jackson.mixin.OpenAPI31Mixin;
import io.swagger.v3.core.jackson.mixin.Schema31Mixin;
import io.swagger.v3.core.util.ApiResponses31Deserializer;
import io.swagger.v3.core.util.Callback31Deserializer;
import io.swagger.v3.core.util.DeserializationModule31;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Model31Deserializer;
import io.swagger.v3.core.util.OpenAPI30To31;
import io.swagger.v3.core.util.OpenAPI31Deserializer;
import io.swagger.v3.core.util.Parameter31Deserializer;
import io.swagger.v3.core.util.Paths31Deserializer;
import io.swagger.v3.core.util.SecurityScheme31Deserializer;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
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
import io.swagger.v3.oas.models.media.*;
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
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.reference.DereferencerContext;
import io.swagger.v3.parser.reference.DereferencersFactory;
import io.swagger.v3.parser.reference.IdsTraverser;
import io.swagger.v3.parser.reference.OpenAPI31Traverser;
import io.swagger.v3.parser.reference.OpenAPIDereferencer;
import io.swagger.v3.parser.reference.Reference;
import io.swagger.v3.parser.reference.ReferenceUtils;
import io.swagger.v3.parser.reference.ReferenceVisitor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationUtils;
import org.openapitools.codegen.utils.ModelUtils;
import org.openapitools.codegen.utils.OnceLogger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Classes which must be registered for reflection in order to GraalVM to include them in the final image.
 */
@RegisterForReflection(targets = {
        JsonDeserializer.class, JsonStreamParser.class, Gson.class, FieldNamingStrategy.class, JsonSerializer.class, JsonNull.class, InstanceCreator.class, JsonSerializationContext.class,
        JsonElement.class, JsonReader.class, JsonToken.class, MalformedJsonException.class, JsonWriter.class, JsonIOException.class, TypeToken.class, TypeAdapter.class, JsonPrimitive.class, ConstructorConstructor.class,
        GsonBuildConfig.class, LazilyParsedNumber.class, JsonReaderInternalAccess.class, ObjectConstructor.class, PreJava9DateFormatProvider.class, Streams.class,
        UnsafeAllocator.class, LinkedTreeMap.class, Primitives.class, TreeTypeAdapter.class, ObjectTypeAdapter.class, JsonAdapterAnnotationTypeAdapterFactory.class, JsonTreeReader.class,
        NumberTypeAdapter.class, ISO8601Utils.class, ReflectiveTypeAdapterFactory.class, DefaultDateTypeAdapter.class,
        CollectionTypeAdapterFactory.class, MapTypeAdapterFactory.class, TypeAdapters.class, JsonTreeWriter.class, ArrayTypeAdapter.class, Excluder.class, JavaVersion.class,
        SqlTypesSupport.class, ToNumberPolicy.class, SerializedName.class, Expose.class, NumberTypeAdapter.class, DryRunEntry.class, CatsTestCaseSummary.class,
        JsonAdapter.class, Until.class, Since.class, TypeAdapterFactory.class, LongSerializationPolicy.class, FieldNamingPolicy.class, JsonSyntaxException.class,
        JsonArray.class, ToNumberStrategy.class, JsonParseException.class, JsonParser.class, GsonBuilder.class, FieldAttributes.class, JsonDeserializationContext.class, JsonObject.class, ExclusionStrategy.class,
        Components.class, ExternalDocumentation.class, Operation.class, PathItem.class,
        Paths.class, Callback.class, Example.class, Header.class, Contact.class,
        Info.class, License.class, Link.class, LinkParameter.class, ArraySchema.class, BinarySchema.class,
        BooleanSchema.class, ByteArraySchema.class, ComposedSchema.class, Content.class, DateSchema.class,
        DateTimeSchema.class, Discriminator.class, EmailSchema.class, Encoding.class, EncodingProperty.class,
        FileSchema.class, IntegerSchema.class, MapSchema.class, MediaType.class, NumberSchema.class, ObjectSchema.class,
        PasswordSchema.class, Schema.class, StringSchema.class, UUIDSchema.class, XML.class,
        CookieParameter.class, HeaderParameter.class, Parameter.class, PathParameter.class, QueryParameter.class,
        RequestBody.class, ApiResponse.class, ApiResponses.class, OAuthFlow.class, OAuthFlows.class, Scopes.class, SecurityRequirement.class,
        SecurityScheme.class, Server.class, ServerVariable.class, ServerVariables.class, Tag.class,
        Configuration.class, Criteria.class, DocumentContext.class, EvaluationListener.class, Filter.class, InvalidCriteriaException.class, InvalidJsonException.class,
        InvalidModificationException.class, InvalidPathException.class, JsonPath.class, JsonPathException.class, MapFunction.class, Option.class, ParseContext.class,
        PathNotFoundException.class, Predicate.class, ReadContext.class, TypeRef.class, ValueCompareException.class,
        WriteContext.class, CharacterIndex.class, DefaultsImpl.class, EvaluationAbortException.class,
        EvaluationContext.class, JsonContext.class, JsonFormatter.class, ParseContextImpl.class, Path.class, PathRef.class, Utils.class, Evaluator.class, EvaluatorFactory.class,
        ExpressionNode.class, FilterCompiler.class, LogicalExpressionNode.class, LogicalOperator.class, PatternFlag.class, RelationalExpressionNode.class, RelationalOperator.class,
        ValueNode.class, ValueNodes.class, ParamType.class, com.jayway.jsonpath.internal.function.Parameter.class, PassthruPathFunction.class, PathFunction.class, PathFunctionFactory.class, Append.class, KeySetFunction.class,
        ILateBindingValue.class, JsonLateBindingValue.class, PathLateBindingValue.class, AbstractAggregation.class, Average.class, Max.class, Min.class, StandardDeviation.class, Sum.class,
        Concatenate.class, Length.class, ArrayIndexOperation.class, ArrayIndexToken.class, ArrayPathToken.class, ArraySliceOperation.class, ArraySliceToken.class, CompiledPath.class,
        EvaluationContextImpl.class, FunctionPathToken.class, PathCompiler.class, PathToken.class, PathTokenAppender.class, PathTokenFactory.class, PredicateContextImpl.class,
        PredicatePathToken.class, RootPathToken.class, ScanPathToken.class, WildcardPathToken.class, Cache.class, CacheProvider.class, LRUCache.class, NOOPCache.class,
        AbstractJsonProvider.class, GsonJsonProvider.class, JacksonJsonNodeJsonProvider.class, JacksonJsonProvider.class, JettisonProvider.class, JsonOrgJsonProvider.class, JsonProvider.class,
        JsonSmartJsonProvider.class, GsonMappingProvider.class, JacksonMappingProvider.class, JsonOrgMappingProvider.class, JsonSmartMappingProvider.class,
        MappingException.class, MappingProvider.class,
        CatsDSLParser.class,
        Base64.Encoder.class, Base64.Decoder.class, Base64.class, StringUtils.class, RandomStringUtils.class, DateFormatUtils.class, DateUtils.class, DurationUtils.class, LocalDate.class, LocalDateTime.class,
        OffsetDateTime.class, String.class, SwaggerConverter.class, SwaggerParserExtension.class, SwaggerParser.class, Swagger20Parser.class, SwaggerResolver.class,
        io.swagger.v3.core.converter.AnnotatedType.class, io.swagger.v3.core.converter.ModelConverter.class, io.swagger.v3.core.converter.ModelConverterContext.class, io.swagger.v3.core.converter.ModelConverterContextImpl.class, io.swagger.v3.core.converter.ModelConverters.class, io.swagger.v3.core.converter.ResolvedSchema.class, io.swagger.v3.core.filter.AbstractSpecFilter.class, io.swagger.v3.core.filter.OpenAPISpecFilter.class, io.swagger.v3.core.filter.SpecFilter.class, io.swagger.v3.core.jackson.AbstractModelConverter.class, io.swagger.v3.core.jackson.ApiResponsesSerializer.class, io.swagger.v3.core.jackson.CallbackSerializer.class, io.swagger.v3.core.jackson.ModelResolver.class, io.swagger.v3.core.jackson.PackageVersion.class, io.swagger.v3.core.jackson.PathsSerializer.class, io.swagger.v3.core.jackson.SchemaSerializer.class, io.swagger.v3.core.jackson.SwaggerAnnotationIntrospector.class, io.swagger.v3.core.jackson.SwaggerModule.class, io.swagger.v3.core.jackson.TypeNameResolver.class, io.swagger.v3.core.jackson.mixin.ComponentsMixin.class, io.swagger.v3.core.jackson.mixin.DateSchemaMixin.class, io.swagger.v3.core.jackson.mixin.ExtensionsMixin.class, io.swagger.v3.core.jackson.mixin.OpenAPIMixin.class, io.swagger.v3.core.jackson.mixin.OperationMixin.class, io.swagger.v3.core.model.ApiDescription.class, io.swagger.v3.core.util.AnnotationsUtils.class, io.swagger.v3.core.util.ApiResponsesDeserializer.class, io.swagger.v3.core.util.CallbackDeserializer.class, io.swagger.v3.core.util.Constants.class, io.swagger.v3.core.util.DeserializationModule.class, io.swagger.v3.core.util.EncodingPropertyStyleEnumDeserializer.class, io.swagger.v3.core.util.EncodingStyleEnumDeserializer.class, io.swagger.v3.core.util.HeaderStyleEnumDeserializer.class, io.swagger.v3.core.util.Json.class, io.swagger.v3.core.util.ModelDeserializer.class, io.swagger.v3.core.util.ObjectMapperFactory.class, io.swagger.v3.core.util.ParameterDeserializer.class, io.swagger.v3.core.util.ParameterProcessor.class, io.swagger.v3.core.util.PathUtils.class, io.swagger.v3.core.util.PathsDeserializer.class, io.swagger.v3.core.util.PrimitiveType.class, io.swagger.v3.core.util.RefUtils.class, io.swagger.v3.core.util.ReflectionUtils.class, io.swagger.v3.core.util.SecuritySchemeDeserializer.class, io.swagger.v3.core.util.Yaml.class, io.swagger.v3.oas.annotations.ExternalDocumentation.class, io.swagger.v3.oas.annotations.Hidden.class, io.swagger.v3.oas.annotations.OpenAPIDefinition.class, io.swagger.v3.oas.annotations.Operation.class, io.swagger.v3.oas.annotations.Parameter.class, io.swagger.v3.oas.annotations.Parameters.class, io.swagger.v3.oas.annotations.callbacks.Callback.class, io.swagger.v3.oas.annotations.callbacks.Callbacks.class, io.swagger.v3.oas.annotations.enums.Explode.class, io.swagger.v3.oas.annotations.enums.ParameterIn.class, io.swagger.v3.oas.annotations.enums.ParameterStyle.class, io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.class, io.swagger.v3.oas.annotations.enums.SecuritySchemeType.class, io.swagger.v3.oas.annotations.extensions.Extension.class, io.swagger.v3.oas.annotations.extensions.ExtensionProperty.class, io.swagger.v3.oas.annotations.extensions.Extensions.class, io.swagger.v3.oas.annotations.headers.Header.class, io.swagger.v3.oas.annotations.info.Contact.class, io.swagger.v3.oas.annotations.info.Info.class, io.swagger.v3.oas.annotations.info.License.class, io.swagger.v3.oas.annotations.links.Link.class, io.swagger.v3.oas.annotations.links.LinkParameter.class, io.swagger.v3.oas.annotations.media.ArraySchema.class, io.swagger.v3.oas.annotations.media.Content.class, io.swagger.v3.oas.annotations.media.DiscriminatorMapping.class, io.swagger.v3.oas.annotations.media.Encoding.class, io.swagger.v3.oas.annotations.media.ExampleObject.class, io.swagger.v3.oas.annotations.media.Schema.class, io.swagger.v3.oas.annotations.parameters.RequestBody.class, io.swagger.v3.oas.annotations.responses.ApiResponse.class, io.swagger.v3.oas.annotations.responses.ApiResponses.class, io.swagger.v3.oas.annotations.security.OAuthFlow.class, io.swagger.v3.oas.annotations.security.OAuthFlows.class, io.swagger.v3.oas.annotations.security.OAuthScope.class, io.swagger.v3.oas.annotations.security.SecurityRequirement.class, io.swagger.v3.oas.annotations.security.SecurityRequirements.class, io.swagger.v3.oas.annotations.security.SecurityScheme.class, io.swagger.v3.oas.annotations.security.SecuritySchemes.class, io.swagger.v3.oas.annotations.servers.Server.class, io.swagger.v3.oas.annotations.servers.ServerVariable.class, io.swagger.v3.oas.annotations.servers.Servers.class, io.swagger.v3.oas.annotations.tags.Tag.class, io.swagger.v3.oas.annotations.tags.Tags.class, io.swagger.v3.oas.models.Components.class, io.swagger.v3.oas.models.ExternalDocumentation.class, io.swagger.v3.oas.models.OpenAPI.class, io.swagger.v3.oas.models.Operation.class, io.swagger.v3.oas.models.PathItem.class, io.swagger.v3.oas.models.Paths.class, io.swagger.v3.oas.models.callbacks.Callback.class, io.swagger.v3.oas.models.examples.Example.class, io.swagger.v3.oas.models.headers.Header.class, io.swagger.v3.oas.models.info.Contact.class, io.swagger.v3.oas.models.info.Info.class, io.swagger.v3.oas.models.info.License.class, io.swagger.v3.oas.models.links.Link.class, io.swagger.v3.oas.models.links.LinkParameter.class, io.swagger.v3.oas.models.media.ArraySchema.class, io.swagger.v3.oas.models.media.BinarySchema.class, io.swagger.v3.oas.models.media.BooleanSchema.class, io.swagger.v3.oas.models.media.ByteArraySchema.class, io.swagger.v3.oas.models.media.ComposedSchema.class, io.swagger.v3.oas.models.media.Content.class, io.swagger.v3.oas.models.media.DateSchema.class, io.swagger.v3.oas.models.media.DateTimeSchema.class, io.swagger.v3.oas.models.media.Discriminator.class, io.swagger.v3.oas.models.media.EmailSchema.class, io.swagger.v3.oas.models.media.Encoding.class, io.swagger.v3.oas.models.media.EncodingProperty.class, io.swagger.v3.oas.models.media.FileSchema.class, io.swagger.v3.oas.models.media.IntegerSchema.class, io.swagger.v3.oas.models.media.MapSchema.class, io.swagger.v3.oas.models.media.MediaType.class, io.swagger.v3.oas.models.media.NumberSchema.class, io.swagger.v3.oas.models.media.ObjectSchema.class, io.swagger.v3.oas.models.media.PasswordSchema.class, io.swagger.v3.oas.models.media.Schema.class, io.swagger.v3.oas.models.media.StringSchema.class, io.swagger.v3.oas.models.media.UUIDSchema.class, io.swagger.v3.oas.models.media.XML.class, io.swagger.v3.oas.models.parameters.CookieParameter.class, io.swagger.v3.oas.models.parameters.HeaderParameter.class, io.swagger.v3.oas.models.parameters.Parameter.class, io.swagger.v3.oas.models.parameters.PathParameter.class, io.swagger.v3.oas.models.parameters.QueryParameter.class, io.swagger.v3.oas.models.parameters.RequestBody.class, io.swagger.v3.oas.models.responses.ApiResponse.class, io.swagger.v3.oas.models.responses.ApiResponses.class, io.swagger.v3.oas.models.security.OAuthFlow.class, io.swagger.v3.oas.models.security.OAuthFlows.class, io.swagger.v3.oas.models.security.Scopes.class, io.swagger.v3.oas.models.security.SecurityRequirement.class, io.swagger.v3.oas.models.security.SecurityScheme.class, io.swagger.v3.oas.models.servers.Server.class, io.swagger.v3.oas.models.servers.ServerVariable.class, io.swagger.v3.oas.models.servers.ServerVariables.class, io.swagger.v3.oas.models.tags.Tag.class, io.swagger.parser.OpenAPIParser.class, io.swagger.v3.parser.converter.SwaggerInventory.class, io.swagger.v3.parser.converter.SwaggerConverter.class, io.swagger.config.ConfigFactory.class, io.swagger.config.FilterFactory.class, io.swagger.config.Scanner.class, io.swagger.config.ScannerFactory.class, io.swagger.config.SwaggerConfig.class, io.swagger.converter.ModelConverter.class, io.swagger.converter.ModelConverterContext.class, io.swagger.converter.ModelConverterContextImpl.class, io.swagger.converter.ModelConverters.class, io.swagger.core.filter.AbstractSpecFilter.class, io.swagger.core.filter.SpecFilter.class, io.swagger.core.filter.SwaggerSpecFilter.class, io.swagger.jackson.AbstractModelConverter.class, io.swagger.jackson.ModelResolver.class, io.swagger.jackson.PackageVersion.class, io.swagger.jackson.SwaggerAnnotationIntrospector.class, io.swagger.jackson.SwaggerModule.class, io.swagger.jackson.TypeNameResolver.class, io.swagger.jackson.mixin.IgnoreOriginalRefMixin.class, io.swagger.jackson.mixin.OriginalRefMixin.class, io.swagger.jackson.mixin.ResponseSchemaMixin.class, io.swagger.util.AllowableEnumValues.class, io.swagger.util.AllowableRangeValues.class, io.swagger.util.AllowableValues.class, io.swagger.util.AllowableValuesUtils.class, io.swagger.util.BaseReaderUtils.class, io.swagger.util.DeserializationModule.class, io.swagger.util.Json.class, io.swagger.util.ModelDeserializer.class, io.swagger.util.ObjectMapperFactory.class, io.swagger.util.ParameterDeserializer.class, io.swagger.util.ParameterProcessor.class, io.swagger.util.PathDeserializer.class, io.swagger.util.PathUtils.class, io.swagger.util.PrimitiveType.class, io.swagger.util.PropertyDeserializer.class, io.swagger.util.ReferenceSerializationConfigurer.class, io.swagger.util.ReflectionUtils.class, io.swagger.util.ResponseDeserializer.class, io.swagger.util.SecurityDefinitionDeserializer.class, io.swagger.util.Yaml.class, io.swagger.models.AbstractModel.class, io.swagger.models.ArrayModel.class, io.swagger.models.ComposedModel.class, io.swagger.models.Contact.class, io.swagger.models.ExternalDocs.class, io.swagger.models.HttpMethod.class, io.swagger.models.Info.class, io.swagger.models.License.class, io.swagger.models.Model.class, io.swagger.models.ModelImpl.class, io.swagger.models.Operation.class, io.swagger.models.Path.class, io.swagger.models.RefModel.class, io.swagger.models.RefPath.class, io.swagger.models.RefResponse.class, io.swagger.models.Response.class, io.swagger.models.Scheme.class, io.swagger.models.SecurityRequirement.class, io.swagger.models.SecurityScope.class, io.swagger.models.Swagger.class, io.swagger.models.Tag.class, io.swagger.models.Xml.class, io.swagger.models.auth.AbstractSecuritySchemeDefinition.class, io.swagger.models.auth.ApiKeyAuthDefinition.class, io.swagger.models.auth.AuthorizationValue.class, io.swagger.models.auth.BasicAuthDefinition.class, io.swagger.models.auth.In.class, io.swagger.models.auth.OAuth2Definition.class, io.swagger.models.auth.SecuritySchemeDefinition.class, io.swagger.models.parameters.AbstractParameter.class, io.swagger.models.parameters.AbstractSerializableParameter.class, io.swagger.models.parameters.BodyParameter.class, io.swagger.models.parameters.CookieParameter.class, io.swagger.models.parameters.FormParameter.class, io.swagger.models.parameters.HeaderParameter.class, io.swagger.models.parameters.Parameter.class, io.swagger.models.parameters.PathParameter.class, io.swagger.models.parameters.QueryParameter.class, io.swagger.models.parameters.RefParameter.class, io.swagger.models.parameters.SerializableParameter.class, io.swagger.models.properties.AbstractNumericProperty.class, io.swagger.models.properties.AbstractProperty.class, io.swagger.models.properties.ArrayProperty.class, io.swagger.models.properties.BaseIntegerProperty.class, io.swagger.models.properties.BinaryProperty.class, io.swagger.models.properties.BooleanProperty.class, io.swagger.models.properties.ByteArrayProperty.class, io.swagger.models.properties.ComposedProperty.class, io.swagger.models.properties.DateProperty.class, io.swagger.models.properties.DateTimeProperty.class, io.swagger.models.properties.DecimalProperty.class, io.swagger.models.properties.DoubleProperty.class, io.swagger.models.properties.EmailProperty.class, io.swagger.models.properties.FileProperty.class, io.swagger.models.properties.FloatProperty.class, io.swagger.models.properties.IntegerProperty.class, io.swagger.models.properties.LongProperty.class, io.swagger.models.properties.MapProperty.class, io.swagger.models.properties.ObjectProperty.class, io.swagger.models.properties.PasswordProperty.class, io.swagger.models.properties.Property.class, io.swagger.models.properties.PropertyBuilder.class, io.swagger.models.properties.RefProperty.class, io.swagger.models.properties.StringProperty.class, io.swagger.models.properties.UUIDProperty.class, io.swagger.models.properties.UntypedProperty.class, io.swagger.models.refs.GenericRef.class, io.swagger.models.refs.RefFormat.class, io.swagger.models.refs.RefType.class, io.swagger.models.utils.PropertyModelConverter.class, io.swagger.annotations.Api.class, io.swagger.annotations.ApiImplicitParam.class, io.swagger.annotations.ApiImplicitParams.class, io.swagger.annotations.ApiKeyAuthDefinition.class, io.swagger.annotations.ApiModel.class, io.swagger.annotations.ApiModelProperty.class, io.swagger.annotations.ApiOperation.class, io.swagger.annotations.ApiParam.class, io.swagger.annotations.ApiResponse.class, io.swagger.annotations.ApiResponses.class, io.swagger.annotations.Authorization.class, io.swagger.annotations.AuthorizationScope.class, io.swagger.annotations.BasicAuthDefinition.class, io.swagger.annotations.Contact.class, io.swagger.annotations.Example.class, io.swagger.annotations.ExampleProperty.class, io.swagger.annotations.Extension.class, io.swagger.annotations.ExtensionProperty.class, io.swagger.annotations.ExternalDocs.class, io.swagger.annotations.Info.class, io.swagger.annotations.License.class, io.swagger.annotations.OAuth2Definition.class, io.swagger.annotations.ResponseHeader.class, io.swagger.annotations.Scope.class, io.swagger.annotations.SecurityDefinition.class, io.swagger.annotations.SwaggerDefinition.class, io.swagger.annotations.Tag.class, io.swagger.parser.SwaggerParserExtension.class, io.swagger.parser.processors.ModelProcessor.class, io.swagger.parser.processors.PathsProcessor.class, io.swagger.parser.processors.DefinitionsProcessor.class, io.swagger.parser.processors.ResponseProcessor.class, io.swagger.parser.processors.ParameterProcessor.class, io.swagger.parser.processors.OperationProcessor.class, io.swagger.parser.processors.ExternalRefProcessor.class, io.swagger.parser.processors.PropertyProcessor.class, io.swagger.parser.SwaggerResolver.class, io.swagger.parser.SwaggerParser.class, io.swagger.parser.ResolverOptions.class, io.swagger.parser.ResolverCache.class, io.swagger.parser.util.RemoteUrl.class, io.swagger.parser.util.DeserializationUtils.class, io.swagger.parser.util.SwaggerDeserializationResult.class, io.swagger.parser.util.ManagedValue.class, io.swagger.parser.util.PathUtils.class, io.swagger.parser.util.ClasspathHelper.class, io.swagger.parser.util.ParseOptions.class, io.swagger.parser.util.SwaggerDeserializer.class, io.swagger.parser.util.HostAuthorizationValue.class, io.swagger.parser.util.InlineModelResolver.class, io.swagger.parser.util.RefUtils.class, io.swagger.parser.Swagger20Parser.class, io.swagger.v3.parser.core.extensions.SwaggerParserExtension.class, io.swagger.v3.parser.core.models.AuthorizationValue.class, io.swagger.v3.parser.core.models.SwaggerParseResult.class, io.swagger.v3.parser.core.models.ParseOptions.class, io.swagger.v3.parser.processors.ExampleProcessor.class, io.swagger.v3.parser.processors.PathsProcessor.class, io.swagger.v3.parser.processors.SecuritySchemeProcessor.class, io.swagger.v3.parser.processors.HeaderProcessor.class, io.swagger.v3.parser.processors.SchemaProcessor.class, io.swagger.v3.parser.processors.RequestBodyProcessor.class, io.swagger.v3.parser.processors.CallbackProcessor.class, io.swagger.v3.parser.processors.ResponseProcessor.class, io.swagger.v3.parser.processors.ComponentsProcessor.class, io.swagger.v3.parser.processors.ParameterProcessor.class, io.swagger.v3.parser.processors.OperationProcessor.class, io.swagger.v3.parser.processors.LinkProcessor.class, io.swagger.v3.parser.processors.ExternalRefProcessor.class, io.swagger.v3.parser.OpenAPIResolver.class, io.swagger.v3.parser.ObjectMapperFactory.class, io.swagger.v3.parser.OpenAPIV3Parser.class, io.swagger.v3.parser.ResolverCache.class, io.swagger.v3.parser.util.RemoteUrl.class, io.swagger.v3.parser.util.DeserializationUtils.class, io.swagger.v3.parser.util.ReferenceValidator.class, io.swagger.v3.parser.util.ManagedValue.class, io.swagger.v3.parser.util.PathUtils.class, io.swagger.v3.parser.util.ClasspathHelper.class, io.swagger.v3.parser.util.ResolverFully.class, io.swagger.v3.parser.util.SchemaTypeUtil.class, io.swagger.v3.parser.util.InlineModelResolver.class, io.swagger.v3.parser.util.RefUtils.class, io.swagger.v3.parser.util.OpenAPIDeserializer.class, io.swagger.v3.parser.exception.ReadContentException.class, io.swagger.v3.parser.exception.EncodingNotSupportedException.class, io.swagger.v3.parser.models.RefType.class, io.swagger.v3.parser.models.RefFormat.class,
        FuzzerListEntry.class, FuzzerListEntry.FuzzerDetails.class, TimeExecutionDetails.class, TimeExecution.class, CatsTestReport.class, CatsResultFactory.class, CatsResultFactory.CatsResult.class, CatsTestCase.class, KeyValuePair.class, CatsResponse.class, CatsRequest.class, PathListEntry.class, PathListEntry.PathDetails.class, InfoCommand.CatsInfo.class, StatsCommand.Stats.class, PathDetailsEntry.class, PathDetailsEntry.OperationDetails.class, ValidContractEntry.class, OnceLogger.class, ModelUtils.class, JsonSchema.class, MutatorEntry.class,
        Name.class, Address.class, EnFile.class, FakeValuesGrouping.class, FakeValues.class, RandomService.class, FakeValuesService.class, UUID.class, net.minidev.asm.ConvertDate.class, net.minidev.asm.DefaultConverter.class, OffsetDateTime.class, LocalDateTime.class, LocalDate.class, OffsetTime.class,
        RgxGenOption.class, RgxGenProperties.class, ArrayIteratorSupplier.class, ChoiceIteratorSupplier.class, GroupIteratorSupplier.class, IncrementalLengthIteratorSupplier.class, NegativeIteratorSupplier.class, PermutationsIteratorSupplier.class, ReferenceIteratorSupplier.class, SingleCaseInsensitiveValueIteratorSupplier.class, SingleValueIteratorSupplier.class, ArrayIterator.class, CaseVariationIterator.class, ChoiceIterator.class, IncrementalLengthIterator.class, NegativeStringIterator.class, PermutationsIterator.class, ReferenceIterator.class, SingleValueIterator.class,
        StringIterator.class, Choice.class, FinalSymbol.class, Group.class, GroupRef.class, Node.class, NotSymbol.class, Repeat.class, Sequence.class, SymbolSet.class, CharIterator.class, DefaultTreeBuilder.class, NodeTreeBuilder.class, Util.class, GenerationVisitor.class,
        GenerationVisitorBuilder.class, GenerationVisitorCaseInsensitive.class, NodeVisitor.class, NotMatchingCaseInsensitiveGenerationVisitor.class, NotMatchingGenerationVisitor.class, UniqueGenerationVisitor.class, UniqueValuesCountingVisitor.class, RgxGen.class, JavaTimeModule.class, OpenAPI30.class, OpenAPI31.class,
        DereferencerContext.class, DereferencersFactory.class, IdsTraverser.class, OpenAPI31Traverser.class, OpenAPIDereferencer.class, Reference.class, ReferenceUtils.class, ReferenceVisitor.class, ApiResponses31Deserializer.class, Callback31Deserializer.class, DeserializationModule31.class, Json31.class, Model31Deserializer.class, OpenAPI30To31.class, OpenAPI31Deserializer.class, Parameter31Deserializer.class, Paths31Deserializer.class, SecurityScheme31Deserializer.class, Yaml31.class, OpenAPI31SpecFilter.class, Components31Mixin.class, Discriminator31Mixin.class, Info31Mixin.class,
        OpenAPI31Mixin.class, Schema31Mixin.class, Schema31Serializer.class
})
public class ReflectionConfig {
}
