
### LargeValuesInHeadersFuzzer
This `Fuzzer` will send large values in the request headers. It will iterate through each header and fuzz it with a large value. All the other headers and the request body and query string will be similar to a 'normal' request. This `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `4XX` code. In case the response code is a documented one, this will be reported with an `INFO` level log message, otherwise with a `WARN` level message.
- If the service responds with a `2XX` code, the `Fuzzer` will report it as an `ERROR` level message.
- Any other case will be reported using an `ERROR` level message.

### RemoveHeadersFuzzer
This `Fuzzer` will create the power set of the headers set. It will then iterate through all those sets and remove them from the payload. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `4XX` code in the case when required headers were removed and with a `2XX` code in the case of optional headers being removed. If the response code is a documented one, this will be reported as an `INFO` level message, otherwise as a `WARN` message.
- In the case that the request has at least one required header removed and the service responds with a `2XX` code, this will be reported as an `ERROR` message.
- In the case that the request didn't have any required headers removed and the service response is a `2XX` code, this is expected behaviour and will be reported as an `INFO` level log message.
- In the case where the request didn't have any required headers removed, but the service responded with a `4XX` or `5XX` code, this is abnormal behaviour and will be reported as an `ERROR` message.
- Any other case is considered abnormal behaviour and will be reported as an `ERROR` message.

Please note: **When the RemoveHeadersFuzzer is running any security (either named `authorization` or `jwt`) header mentioned in the `headers.yml` will be added to the requests.**

### DummyContentTypeHeadersFuzzer,  DummyAcceptHeadersFuzzer, UnsupportedTypeHeadersFuzzer, UnsupportedAcceptHeadersFuzzer
These `Fuzzers` are implementing the [OWASP REST API recommendations](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html).
They check that the API has correctly set the `Content-Type` and `Accept` headers and no invalid values can be supplied.

The `Fuzzers` expect:
- `406` for unsupported or invalid `Accept` headers
- `415` for unsupported or invalid `Content-Type` headers

### CheckSecurityHeadersFuzzer
This `Fuzzer` will continue the [OWASP REST API recommendations](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html) by checking
a list of required Security headers that must be supplied in each response.

The `Fuzzer` expects a `2XX` response with the following headers set:
- `Cache-Control: no-store`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
