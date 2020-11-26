package com.endava.cats.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.http.Header;

import java.io.StringReader;
import java.util.List;

public class CatsRequest {
    List<Header> headers;
    JsonElement payload;

    public static CatsRequest empty() {
        CatsRequest request = new CatsRequest();
        request.payload = JsonParser.parseReader(new StringReader("{}"));
        return request;
    }

    public void setPayload(String payload) {
        JsonReader reader = new JsonReader(new StringReader(payload));
        reader.setLenient(true);
        this.payload = JsonParser.parseReader(reader);
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }
}
