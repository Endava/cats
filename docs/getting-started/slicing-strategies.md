---
sidebar_position: 5
description: How to get meaningful results in a timely manner
---

# Slicing Strategies

CATS has more than **110** fuzzers at the moment. Some Fuzzers are executing multiple tests for every given field within the request.
For example the `ControlCharsOnlyInFieldsFuzzer` has **63** control chars values that will be tried for each request field. If a request has 15 fields, this will result in **945 tests**.
Considering that there are additional Fuzzers with the same magnitude, you can easily get to 20k tests being executed on a typical run. 
This will result in huge reports and long-running times (i.e. minutes, rather than seconds).

Below are some recommended strategies on how you can separate the tests in chunks which can be executed as stages in a deployment pipeline, one after the other.

:::caution
Running CATS with **all** Fuzzers and `--verbosity=detailed` (or without verbosity, before CATS 10.x) will produce a significant amount of logging. 
Please make sure you have a purging strategy in place, especially when choosing to store the output in files. Additionally, you can control the logging level using the `--log` argument.
:::

## Slice by Endpoints
You can use the `--paths=PATH` argument to run CATS sequentially for each path.

## Slice by Fuzzer Category
You can use the `--checkXXX` arguments to run CATS only with specific Fuzzers like: `--checkHttp`, `-checkFields`, etc. See [available arguments](/docs/commands-and-arguments/arguments) for a complete list of `--checkXXX` arguments.

## Slice by Fuzzer Type
You can use various arguments like `--fuzzers=Fuzzer1,Fuzzer2` or `-skipFuzzers=Fuzzer1,Fuzzer2` to either include or exclude specific Fuzzers.
For example, you can run all Fuzzers except for the `Boundary` Fuzzers like this: `--skipFuzzers=Boundary`. This will skip all Fuzzers containing `Boundary` in their name.
After, you can create an additional run only with these Fuzzers using`--fuzzers=Boundary`.

## Slide by Tags
You can use the `--tag=TAG` argument to run CATS sequentially for each tag. You can also supply a comma separated list of tags to run CATS for multiple tags like this: `--tags=tag1,tag2`.

These are just some recommendations. Depending on how complex your API is, you might go with a combination of the above or with even more granular splits.

:::note
Please note that due to the fact that `ControlChars, Emojis and Whitespaces` Fuzzers generate huge number of tests even for small OpenAPI contracts, they are disabled by default.
You can enable them using the `--includeControlChars`, `--includeWhitespaces` and/or `--includeEmojis` arguments.
The recommendation is to run them in separate runs so that you get manageable reports and optimal running times.
:::
