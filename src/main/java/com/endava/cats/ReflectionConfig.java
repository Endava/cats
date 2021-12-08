package com.endava.cats;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.callbacks.Callback;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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

@RegisterForReflection(targets = {
        Components.class, ExternalDocumentation.class, Operation.class, PathItem.class,
        Paths.class, Callback.class, Example.class, Header.class, Contact.class,
        Info.class, License.class, Link.class, LinkParameter.class, ArraySchema.class, BinarySchema.class,
        BooleanSchema.class, ByteArraySchema.class, ComposedSchema.class, Content.class, DateSchema.class,
        DateTimeSchema.class, Discriminator.class, EmailSchema.class, Encoding.class, EncodingProperty.class,
        FileSchema.class, IntegerSchema.class, MapSchema.class, MediaType.class, NumberSchema.class, ObjectSchema.class,
        PasswordSchema.class, Schema.class, StringSchema.class, UUIDSchema.class, XML.class,
        CookieParameter.class, HeaderParameter.class, Parameter.class, PathParameter.class, QueryParameter.class,
        RequestBody.class, ApiResponse.class, ApiResponses.class, OAuthFlow.class, OAuthFlows.class, Scopes.class, SecurityRequirement.class,
        SecurityScheme.class, Server.class, ServerVariable.class, ServerVariables.class, Tag.class
})
public class ReflectionConfig {
}
