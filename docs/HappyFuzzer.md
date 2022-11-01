### HappyFuzzer
This `Fuzzer` will send a full request to the service, including all fields and headers. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to return a `2XX` code. This will be reported as an `INFO` message if it's a documented code or as a `WARN` message otherwise.
- Any other case is considered abnormal behaviour and will be reported as an `ERROR` message.

### HttpMethodsFuzzer
This `Fuzzer` will set the http request for any unspecified HTTP method in the contract. The `Fuzzer` will behave as follows:
- Normal behaviour is for the service to respond with a `405` code if the method is not documented in the contract. This is reported as an level `INFO` message.
- If the service responds with a `2XX` code this is considered abnormal behaviour and will be reported as an `ERROR` message.
- Any other case is reported as a `WARN` level message.

### BypassAuthenticationFuzzer
This `Fuzzer` will try to send 'happy' flow requests, but will omit any supplied header which might be used for authentication like: `Authorization` or headers containing `JWT`.
The expected result is a `401` or `403` response code.

### MalformedJsonFuzzer
This `Fuzzer` will send a malformed JSON to the service and expects a validation error. The malformed JSON is obtained by taking a valid JSON from the `HappyFuzzer` and append the word `bla` at the end.

**Please note that because the CATS report will only display valid JSON files for both request and responses, the final report won't display the malformed JSON which includes the `bla` string at the end.
No need to worry, as CATS is actually sending the right malformed data to the service. You can check the running logs for the line starting with `Final payload:` to see the exact string which is being send to the service.**

