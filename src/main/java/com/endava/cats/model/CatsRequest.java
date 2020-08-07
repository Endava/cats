package com.endava.cats.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.http.Header;

import java.io.StringReader;
import java.util.List;

public class CatsRequest {
    private List<Header> headers;
    private JsonElement payload;

    public static CatsRequest empty() {
        CatsRequest request = new CatsRequest();
        request.payload = JsonParser.parseReader(new StringReader("{}"));
        return request;
    }

    public JsonElement getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        this.payload = JsonParser.parseReader(reader);
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }
}
