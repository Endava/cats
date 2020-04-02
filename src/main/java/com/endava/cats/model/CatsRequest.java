package com.endava.cats.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.http.Header;

import java.io.StringReader;
import java.util.List;

public class CatsRequest {
    private static final JsonParser jsonParser = new JsonParser();
    private List<Header> headers;
    private JsonElement payload;

    public JsonElement getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        this.payload = jsonParser.parse(reader);
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }

    public static CatsRequest empty() {
        CatsRequest request = new CatsRequest();
        request.payload = jsonParser.parse(new StringReader("{}"));
        return request;
    }
}
