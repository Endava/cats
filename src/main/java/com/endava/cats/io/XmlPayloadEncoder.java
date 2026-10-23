package com.endava.cats.io;

import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.XML;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes JSON payloads into XML strings using OpenAPI {@link XML} schema metadata
 * ({@code xml.name}, {@code xml.attribute}, and {@code xml.wrapped}).
 */
public final class XmlPayloadEncoder {

    private XmlPayloadEncoder() {
        // encoder class
    }

    /**
     * Checks whether the given content-type header represents an XML media type.
     *
     * @param contentType media type string
     * @return {@code true} if XML media type
     */
    public static boolean isXmlContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/xml".equals(normalized)
                || "text/xml".equals(normalized)
                || normalized.endsWith("+xml");
    }

    /**
     * Converts a JSON payload into an XML document using the root OpenAPI {@link Schema} and component schema map.
     *
     * @param payload   JSON payload string
     * @param reqSchema root request schema
     * @param schemaMap map of component schemas
     * @return serialized XML string, or the raw payload if it is not a JSON object/array
     */
    public static String encode(String payload, Schema<?> reqSchema, Map<String, Schema> schemaMap) {
        return encode(payload, reqSchema, schemaMap, false);
    }

    public static String encode(String payload, Schema<?> reqSchema, Map<String, Schema> schemaMap,
                                boolean stripRootIdAttribute) {
        if (payload == null || payload.isBlank() || !JsonUtils.isValidJson(payload)) {
            return payload == null ? "" : payload;
        }
        try {
            JsonElement rootElement = JsonUtils.parseAsJsonElement(payload);
            Schema<?> resolvedRoot = resolveSchema(reqSchema, schemaMap);
            String rootTagName = resolveXmlName(resolvedRoot, "root");
            if (rootElement.isJsonObject()) {
                JsonObject rootObj = rootElement.getAsJsonObject();
                if (stripRootIdAttribute && rootObj.has("id")) {
                    rootObj = rootObj.deepCopy();
                    rootObj.remove("id");
                }
                return serializeObject(rootTagName, rootObj, resolvedRoot, schemaMap);
            }
            if (rootElement.isJsonArray()) {
                return serializeRootArray(rootTagName, rootElement.getAsJsonArray(), resolvedRoot, schemaMap);
            }
            return escapeXml(rootElement.getAsString());
        } catch (RuntimeException _) {
            return payload;
        }
    }

    private static String serializeObject(String tagName, JsonObject jsonObject, Schema<?> schema,
                                          Map<String, Schema> schemaMap) {
        Schema<?> resolved = resolveSchema(schema, schemaMap);
        Map<String, Schema> properties = resolved != null ? resolved.getProperties() : null;

        StringBuilder attrs = new StringBuilder();
        List<String> children = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String propName = entry.getKey();
            JsonElement propVal = entry.getValue();
            if (propVal == null || propVal.isJsonNull()) {
                continue;
            }
            Schema<?> propSchema = properties != null ? resolveSchema(properties.get(propName), schemaMap) : null;
            String xmlName = resolvePropertyXmlName(propSchema, propName);
            boolean isAttribute = propSchema != null && propSchema.getXml() != null
                    && Boolean.TRUE.equals(propSchema.getXml().getAttribute());

            if (isAttribute && propVal.isJsonPrimitive()) {
                attrs.append(' ').append(xmlName).append("=\"")
                        .append(escapeXml(propVal.getAsString())).append('"');
            } else if (propVal.isJsonPrimitive()) {
                children.add("<" + xmlName + ">" + escapeXml(propVal.getAsString()) + "</" + xmlName + ">");
            } else if (propVal.isJsonObject()) {
                children.add(serializeObject(xmlName, propVal.getAsJsonObject(), propSchema, schemaMap));
            } else if (propVal.isJsonArray()) {
                children.add(serializeArrayProperty(propName, xmlName, propVal.getAsJsonArray(), propSchema, schemaMap));
            }
        }

        if (children.isEmpty()) {
            return "<" + tagName + attrs + "/>";
        }
        return "<" + tagName + attrs + ">" + String.join("", children) + "</" + tagName + ">";
    }

    private static String serializeArrayProperty(String propName, String arrayXmlName, JsonArray array,
                                                 Schema<?> arraySchema, Map<String, Schema> schemaMap) {
        boolean wrapped = arraySchema != null && arraySchema.getXml() != null
                && Boolean.TRUE.equals(arraySchema.getXml().getWrapped());
        Schema<?> itemSchema = arraySchema != null ? resolveSchema(arraySchema.getItems(), schemaMap) : null;
        String itemTagName = resolveXmlName(itemSchema, wrapped ? "item" : arrayXmlName);

        StringBuilder itemsBuilder = new StringBuilder();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonObject()) {
                itemsBuilder.append(serializeObject(itemTagName, element.getAsJsonObject(), itemSchema, schemaMap));
            } else if (element.isJsonPrimitive()) {
                itemsBuilder.append('<').append(itemTagName).append('>')
                        .append(escapeXml(element.getAsString()))
                        .append("</").append(itemTagName).append('>');
            }
        }
        if (wrapped) {
            String wrapperTag = (arraySchema.getXml() != null && arraySchema.getXml().getName() != null)
                    ? arraySchema.getXml().getName()
                    : propName;
            return "<" + wrapperTag + ">" + itemsBuilder + "</" + wrapperTag + ">";
        }
        return itemsBuilder.toString();
    }

    private static String serializeRootArray(String rootTagName, JsonArray array, Schema<?> arraySchema,
                                             Map<String, Schema> schemaMap) {
        Schema<?> itemSchema = arraySchema != null ? resolveSchema(arraySchema.getItems(), schemaMap) : null;
        String itemTagName = resolveXmlName(itemSchema, "item");
        StringBuilder sb = new StringBuilder("<").append(rootTagName).append(">");
        for (JsonElement el : array) {
            if (el != null && el.isJsonObject()) {
                sb.append(serializeObject(itemTagName, el.getAsJsonObject(), itemSchema, schemaMap));
            } else if (el != null && el.isJsonPrimitive()) {
                sb.append('<').append(itemTagName).append('>')
                        .append(escapeXml(el.getAsString()))
                        .append("</").append(itemTagName).append('>');
            }
        }
        sb.append("</").append(rootTagName).append(">");
        return sb.toString();
    }

    private static Schema<?> resolveSchema(Schema<?> schema, Map<String, Schema> schemaMap) {
        if (schema == null) {
            return null;
        }
        if (schema.get$ref() != null && schemaMap != null) {
            String simpleRef = CatsModelUtils.getSimpleRef(schema.get$ref());
            Schema<?> resolved = schemaMap.get(simpleRef);
            if (resolved != null) {
                return resolved;
            }
        }
        return schema;
    }

    private static String resolveXmlName(Schema<?> schema, String fallback) {
        if (schema != null && schema.getXml() != null && schema.getXml().getName() != null
                && !schema.getXml().getName().isBlank()) {
            return schema.getXml().getName();
        }
        return fallback;
    }

    private static String resolvePropertyXmlName(Schema<?> propSchema, String propName) {
        if (propSchema != null && propSchema.getXml() != null && propSchema.getXml().getName() != null
                && !propSchema.getXml().getName().isBlank()) {
            return propSchema.getXml().getName();
        }
        return propName;
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
