---
sidebar_position: 6
description: How to ignore specific HTTP response
---

# Filtering Reports
CATS reports can be huge. Opening a report with 20k tests it's not the best user experience. 
Outside the typical [Slicing Strategies](slicing-strategies), you can also choose to ignore reporting for specific HTTP responses.


## Ignoring Specific HTTP Responses
By default, CATS will report `warns` and `errors` according to the specific behaviour of each Fuzzer. There are cases though when you might want to focus only on critical bugs.
You can use the `--ignoreResponseXXX` arguments to supply a list of response codes, response sizes, word counts, 
line counts or response body regexes that should be ignored as issues (overriding the Fuzzer behaviour) and report those cases as `success` instead or `warn` or `error`.
For example, if you want CATS to report `errors` only when there is an Exception or the service returns a `500`, you can use this: `--ignoreResultCodes="2xx,4xx"`.
This is the list with all ignore arguments: `--ignoreResponseCodes, --ignoreResponseLines, --ignoreResponseRegex, --ignoreResponseSize, --ignoreResponseWords`.

:::info Skip Reporting for Ignored Responses
You can choose to skip reporting for ignored HTTP responses using the `--skipReportingForIgnored` argument. This will remove tests matching any `--ignoreXXX` criteria from the final report.
:::

## Ignoring Undocumented Response Code Checks
You can also choose to ignore checks done by the Fuzzers. By default, each Fuzzer has an expected response code, based on the scenario under test and will report an `warn` when the service returns the expected response code,
but the response code is not documented inside the contract.
You can make CATS ignore the undocumented response code checks (i.e. checking expected response code inside the contract) using the `--ignoreResponseCodeUndocumentedCheck` argument. CATS with now report these cases as `success` instead of `warn`.

## Ignoring Response Body Checks
Additionally, you can also choose to ignore the response body checks. By default, on top of checking the expected response code, each Fuzzer will check if the response body matches the response schema defined in the contract and will report an `warn` if not matching.
You can make CATS ignore the response body checks using the `--ingoreResponseBodyCheck` argument. CATS with now report these cases as `success` instead of `warn`.

## Ignore Response Content-Type Checks
By default, CATS will check if the response `Content-Type` header matches the expected one from the contract. If the response `Content-Type` header does not match the expected one, CATS will report an `warn`.
You can make CATS ignore the response `Content-Type` checks using the `--ignoreResponseContentTypeCheck` argument. CATS will now report these cases as `success` instead of `warn`.

## Ignore Response Codes
You can ignore specific response codes using the `--ignoreResponseCodes` argument. This will make CATS report these cases as `success` instead of `warn`.

## Ignore Response Lines, Words, Size, Regex
You can ignore specific response lines, words, size or regex using the `--ignoreResponseLines`, `--ignoreResponseWords`, `--ignoreResponseSize`, `--ignoreResponseRegex` arguments. This will make CATS report these cases as `success` instead of `warn`.

:::info All above `--ignoreXXX` arguments have a corresponding --filterXXX argument
All `--ignoreXXX` arguments above have a corresponding `--filterXXX` argument which is equivalent to the `--ignoreXXX` argument AND will also consider `--skipReportingForIgnore` as being true.
:::

## Ignore Error Leaks Details Checks

By default, CATS will check if the error message contains sensitive information and will report an `error` if it does.
You can make CATS ignore the error leaks details checks using the `--ignoreErrorLeaksCheck` argument.


