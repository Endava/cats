---
sidebar_position: 15
description: How to specify your own dictionary
---

# Custom Dictionaries

CATS has a robust feature set, particularly in its inclusion of built-in fuzzers.
This means it already covers a broad range of fuzzing scenarios and payloads straight out of the box.

Moreover, if you wish to provide your own dictionary, you have several options:

- using the `cats run ...` sub-command and take advantage of
  the [SecurityFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/security-fuzzer).
  This provides greater control over the fuzzing process, allowing you to target specific fields, field types, headers, or the entire request body.
  Custom assertions on response contents can also be set.
- Alternatively, use the root `cats ...` command and provide a custom dictionary using the `-w` argument. More
  details [here](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/user-dictionary).
  While this option is less flexible in terms of fuzzing scope, it offers additional matching capabilities on received responses through
  the `--matchXXX` arguments.

:::tip
You can configure a fuzzing pipeline with multiple stages based on fuzzing type: CATS Base, XSS, SQL Injection, etc.
:::
