package com.endava.cats.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A simple collection backed by a Set that stores unique Jsons. A Json is considered unique if it has a different set of keys.
 */
public class JsonSet {

    private final Set<JsonKeyWrapper> set;

    /**
     * Creates a new JsonSet instance.
     */
    public JsonSet() {
        this.set = new HashSet<>();
    }

    /**
     * Adds a new Json to the set.
     *
     * @param jsonString the Json to be added
     * @return true if the Json was added, false otherwise
     */
    public boolean add(String jsonString) {
        return set.add(new JsonKeyWrapper(jsonString));
    }

    /**
     * Checks if the set contains a Json.
     *
     * @param jsonString the Json to be checked
     * @return true if the Json is in the set, false otherwise
     */
    public boolean contains(String jsonString) {
        return set.contains(new JsonKeyWrapper(jsonString));
    }

    /**
     * Returns the size of the set.
     *
     * @return the size of the set
     */
    public int size() {
        return set.size();
    }

    private static class JsonKeyWrapper {
        private final Set<String> keyTypeSet;

        public JsonKeyWrapper(String jsonString) {
            if (JsonUtils.isValidJson(jsonString)) {
                JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
                this.keyTypeSet = extractKeyTypes(jsonObject);
            } else {
                this.keyTypeSet = new HashSet<>();
                this.keyTypeSet.add(jsonString);
            }
        }

        private Set<String> extractKeyTypes(JsonObject jsonObject) {
            Set<String> keyTypes = new HashSet<>();
            for (String key : jsonObject.keySet()) {
                JsonElement value = jsonObject.get(key);
                if (value.isJsonObject()) {
                    keyTypes.add(key + ":object");
                    keyTypes.addAll(extractKeyTypes(value.getAsJsonObject()));
                } else if (value.isJsonArray()) {
                    keyTypes.add(key + ":array");
                    keyTypes.addAll(extractArrayKeyTypes(value.getAsJsonArray()));
                } else if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isBoolean()) {
                        keyTypes.add(key + ":boolean");
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        keyTypes.add(key + ":number");
                    } else if (value.getAsJsonPrimitive().isString()) {
                        keyTypes.add(key + ":string");
                    }
                } else {
                    keyTypes.add(key + ":null");
                }
            }
            return keyTypes;
        }

        private Set<String> extractArrayKeyTypes(com.google.gson.JsonArray jsonArray) {
            Set<String> keyTypes = new HashSet<>();
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    keyTypes.addAll(extractKeyTypes(element.getAsJsonObject()));
                } else if (element.isJsonArray()) {
                    keyTypes.addAll(extractArrayKeyTypes(element.getAsJsonArray()));
                } else if (element.isJsonPrimitive()) {
                    if (element.getAsJsonPrimitive().isBoolean()) {
                        keyTypes.add("array_item:boolean");
                    } else if (element.getAsJsonPrimitive().isNumber()) {
                        keyTypes.add("array_item:number");
                    } else if (element.getAsJsonPrimitive().isString()) {
                        keyTypes.add("array_item:string");
                    }
                } else {
                    keyTypes.add("array_item:null");
                }
            }
            return keyTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JsonKeyWrapper that = (JsonKeyWrapper) o;
            return Objects.equals(keyTypeSet, that.keyTypeSet);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyTypeSet);
        }
    }
}
