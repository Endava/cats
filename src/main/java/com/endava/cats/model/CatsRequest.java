package com.endava.cats.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.StringReader;
import java.util.List;

@Getter
public class CatsRequest {
    List<Header> headers;
    JsonElement payload;
    String httpMethod;


    public static CatsRequest empty() {
        CatsRequest request = new CatsRequest();
        request.payload = JsonParser.parseReader(new StringReader("{}"));
        request.httpMethod = "";
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

    public void setHttpMethod(String method) {
        this.httpMethod = method;
    }

    @AllArgsConstructor
    public static class Header {
        private String name;
        private String value;
    }
}
