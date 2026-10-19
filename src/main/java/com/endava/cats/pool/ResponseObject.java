package com.endava.cats.pool;

import com.google.gson.JsonObject;

record ResponseObject(JsonObject value, String sourcePrefix) {
}
