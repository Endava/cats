# Limitations

## Native Binaries
When using the native binaries (not affecting the uberjar) there might be issues when using dynamic values in the configuration files.
This is due to the fact that GraalVM only bundles whatever can discover at compile time.
The following classes are currently supported:

```java
  java.util.Base64.Encoder.class, java.util.Base64.Decoder.class, java.util.Base64.class,
  org.apache.commons.lang3.RandomUtils.class, org.apache.commons.lang3.RandomStringUtils.class, 
  org.apache.commons.lang3.DateFormatUtils.class, org.apache.commons.lang3.DateUtils.class, 
  org.apache.commons.lang3.DurationUtils.class, java.time.LocalDate.class, 
  java.time.LocalDateTime.class,java.time.OffsetDateTime.class
```

## API specs
At this moment, CATS only works with OpenAPI specs and has limited functionality using template payloads through the `cats fuzz ...` subcommand.

## Media types and HTTP methods
The Fuzzers have the following support for media types and HTTP methods:
- `application/json` and `application/x-www-form-urlencoded` media types only
- HTTP methods: `POST`, `PUT`, `PATCH`, `GET` and `DELETE`

## Additional Parameters
If a response contains a free Map specified using the `additionalParameters` tag CATS will flag results as `warn` as it won't be able to validate that the response matches the schema.

## Regexes within 'pattern'
CATS uses a mix of regex and data/format driven generators in order to generate Strings based on `patern` and `format`. This has certain limitations mostly with complex patterns.
