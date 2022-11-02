---
sidebar_position: 2
description: Fuzz non-OpenAPI endpoints
---

# Template Fuzzer
The `TemplateFuzzer` can be used to fuzz non-OpenAPI endpoints using the `cats fuzz` sub-command. 
If the target API does not have an OpenAPI spec available, you can use a request template to run a limited set of payloads.
The syntax for running the `TemplateFuzzer` is very similar to `curl`:

```shell
cats fuzz -H header=value -X POST -d '{"field1":"value1","field2":"value2","field3":"value3"}' -t "field1,field2,header" -i "2XX,4XX" http://service-url 
```

:::tip
For nested objects you must supply fully qualified names: `field.subfield`.
:::

The command will:
- send a `POST` (`-X` argument) request to `http://service-url`
- use the `{"field1":"value1","field2":"value2","field3":"value3"}` (`-d` argument) as a template
- iteratively replace each of the `field1,field2,header` elements (`-t` argument) with fuzz data and send each request to the service endpoint
- ignore `2XX,4XX` response codes (`-i` argument) and report an `error` when the received response code is not in this list

:::info
It was a deliberate choice to limit the fields for which the `TemplateFuzzer` will run by supplying them using the `-t` argument. 
:::


The `TemplateFuzzer` will send the following type of data:
- null values
- empty values
- zalgo text
- abugidas characters
- large random unicode data
- very large strings (80k characters)
- single and multi code point emojis
- unicode control characters
- unicode separators
- unicode whitespaces

For a full list of arguments run `cats fuzz -h`.

:::note
You can also supply your own dictionary of data using the `-w file` argument.
:::

:::info
HTTP methods with bodies will only be fuzzed at the request payload and headers level.
HTTP methods without bodies will be fuzzed at path and query parameters and headers level. In this case you don't need to supply  a `-d` argument.
:::

This is an example for a `GET` request:

```shell
cats fuzz -X GET -t "path1,query1" -i "2XX,4XX" http://service-url/paths1?query1=test&query2
```