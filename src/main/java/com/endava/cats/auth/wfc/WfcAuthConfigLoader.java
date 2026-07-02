package com.endava.cats.auth.wfc;

import com.endava.cats.exception.CatsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.webfuzzing.commons.auth.Auth;
import com.webfuzzing.commons.auth.AuthenticationInfo;
import com.webfuzzing.commons.auth.Configs;
import com.webfuzzing.commons.auth.Header;
import com.webfuzzing.commons.auth.LoginEndpoint;
import com.webfuzzing.commons.auth.PayloadUsernamePassword;
import com.webfuzzing.commons.auth.TokenHandling;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads and validates WFC Auth documents.
 */
public class WfcAuthConfigLoader {
    private static final String DEFAULT_TOKEN_SEND_TEMPLATE = "{token}";
    private final ObjectMapper mapper = YAMLMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    /**
     * Loads a WFC Auth file.
     *
     * @param file the YAML/JSON WFC Auth file
     * @return parsed WFC Auth config
     */
    public Auth load(File file) {
        try {
            JsonNode root = mapper.readTree(file);
            if (root == null || !root.isObject()) {
                throw invalid("WFC Auth file must contain a YAML/JSON object");
            }

            JsonNode authNode = root.get("auth");
            if (!(authNode instanceof ArrayNode authArray)) {
                throw invalid("WFC Auth file must contain an auth array");
            }
            if (authArray.isEmpty()) {
                throw invalid("WFC Auth file must contain at least one auth entry");
            }

            ObjectNode authTemplate = readObjectOrEmpty(root.get("authTemplate"), "authTemplate");
            List<AuthenticationInfo> authEntries = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();

            for (JsonNode entry : authArray) {
                if (!entry.isObject()) {
                    throw invalid("Each WFC auth entry must be an object");
                }

                ObjectNode mergedEntry = authTemplate.deepCopy();
                merge(mergedEntry, (ObjectNode) entry);
                AuthenticationInfo auth = mapper.treeToValue(mergedEntry, AuthenticationInfo.class);
                validateAuthenticationInfo(auth);
                if (!names.add(auth.getName())) {
                    throw invalid("Duplicate WFC auth name: " + auth.getName());
                }
                authEntries.add(auth);
            }

            Auth config = new Auth();
            config.setSchemaVersion(text(root, "schemaVersion"));
            config.setAuth(List.copyOf(authEntries));
            if (!authTemplate.isEmpty()) {
                config.setAuthTemplate(mapper.convertValue(authTemplate, Object.class));
            }
            config.setConfigs(parseConfigs(root.get("configs")));
            return config;
        } catch (IOException e) {
            throw new CatsException("Unable to read WFC Auth file " + file, e);
        }
    }

    /**
     * Selects the requested auth entry.
     *
     * @param config        parsed WFC config
     * @param requestedName optional auth entry name
     * @return selected auth entry
     */
    public AuthenticationInfo select(Auth config, String requestedName) {
        if (StringUtils.isBlank(requestedName)) {
            return config.getAuth().getFirst();
        }

        return config.getAuth()
                .stream()
                .filter(auth -> auth.getName().equals(requestedName))
                .findFirst()
                .orElseThrow(() -> invalid("No WFC auth entry named " + requestedName));
    }

    private Configs parseConfigs(JsonNode node) {
        if (isAbsent(node)) {
            return null;
        }
        if (!node.isObject()) {
            throw invalid("configs must be an object");
        }

        Configs configs = new Configs();
        node.properties().forEach(entry -> {
            if (!entry.getValue().isValueNode()) {
                throw invalid("configs." + entry.getKey() + " must be a string value");
            }
            configs.setAdditionalProperty(entry.getKey(), entry.getValue().asText());
        });
        return configs;
    }

    private void validateAuthenticationInfo(AuthenticationInfo auth) {
        require(auth.getName(), "auth.name");
        if (Boolean.TRUE.equals(auth.getRequireMockHandling())) {
            throw invalid("WFC auth entry " + auth.getName() + " requires mock handling, which is not supported by CATS black-box execution");
        }
        if (headers(auth.getFixedHeaders()).isEmpty() && auth.getLoginEndpointAuth() == null) {
            throw invalid("WFC auth entry " + auth.getName() + " must define fixedHeaders or loginEndpointAuth");
        }
        headers(auth.getFixedHeaders()).forEach(header -> validateHeader(header, "fixedHeaders"));
        if (auth.getLoginEndpointAuth() != null) {
            validateLoginEndpoint(auth.getLoginEndpointAuth(), auth.getName());
        }
    }

    private void validateLoginEndpoint(LoginEndpoint login, String authName) {
        require(login.getVerb(), "loginEndpointAuth.verb");

        boolean hasEndpoint = StringUtils.isNotBlank(login.getEndpoint());
        boolean hasExternalEndpoint = StringUtils.isNotBlank(login.getExternalEndpointURL());
        if (hasEndpoint == hasExternalEndpoint) {
            throw invalid("WFC loginEndpointAuth for " + authName + " must define exactly one of endpoint or externalEndpointURL");
        }

        if (login.getPayloadUserPwd() != null) {
            validatePayloadUserPwd(login.getPayloadUserPwd());
        }
        headers(login.getHeaders()).forEach(header -> validateHeader(header, "loginEndpointAuth.headers"));

        if (login.getToken() != null) {
            validateToken(login.getToken());
        }

        if (!Boolean.TRUE.equals(login.getExpectCookies()) && login.getToken() == null) {
            throw invalid("WFC loginEndpointAuth for " + authName + " must define token handling or expectCookies=true");
        }
    }

    private void validatePayloadUserPwd(PayloadUsernamePassword payload) {
        require(payload.getUsername(), "payloadUserPwd.username");
        require(payload.getPassword(), "payloadUserPwd.password");
        require(payload.getUsernameField(), "payloadUserPwd.usernameField");
        require(payload.getPasswordField(), "payloadUserPwd.passwordField");
    }

    private void validateToken(TokenHandling token) {
        require(token.getExtractFrom(), "token.extractFrom");
        require(token.getExtractSelector(), "token.extractSelector");
        require(token.getSendIn(), "token.sendIn");
        require(token.getSendName(), "token.sendName");
        if (StringUtils.isBlank(token.getSendTemplate())) {
            token.setSendTemplate(DEFAULT_TOKEN_SEND_TEMPLATE);
        }
    }

    private void validateHeader(Header header, String fieldName) {
        require(header.getName(), fieldName + ".name");
        require(header.getValue(), fieldName + ".value");
    }

    private void merge(ObjectNode target, ObjectNode override) {
        override.properties().forEach(entry -> {
            JsonNode existing = target.get(entry.getKey());
            JsonNode value = entry.getValue();
            if (existing instanceof ObjectNode existingObject && value instanceof ObjectNode valueObject) {
                merge(existingObject, valueObject);
            } else {
                target.set(entry.getKey(), value);
            }
        });
    }

    private ObjectNode readObjectOrEmpty(JsonNode node, String fieldName) {
        if (isAbsent(node)) {
            return mapper.createObjectNode();
        }
        if (!node.isObject()) {
            throw invalid(fieldName + " must be an object");
        }
        return (ObjectNode) node;
    }

    private static List<Header> headers(List<Header> headers) {
        return headers == null ? List.of() : headers;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static void require(Object value, String field) {
        if (value == null || (value instanceof String text && StringUtils.isBlank(text))) {
            throw invalid("Missing required WFC field: " + field);
        }
    }

    private static CatsException invalid(String message) {
        return new CatsException(message);
    }
}
