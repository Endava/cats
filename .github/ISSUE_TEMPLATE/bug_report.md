---
name: Bug report
about: Create a report to help us improve
title: ''
labels: bug
assignees: en-milie

---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behaviour:
1. Run `cats --contract ...` or `cats run --contract ...` or `cats fuzz ...`
2. Using which OpenAPI file (or redacted version/section of it)
3. And any additional headers provided using `-H` or `--headers`
4. And any relevant other arguments
5. See error

**Expected behaviour**
A clear and concise description of what you expected to happen.

**Environment:**
* Provide the output of: `cats info` or `java -jar cats.jar`
* OpenAPI file: it would be great if you can provide the OpenAPI file used, a redacted version of it or just the section related to the bug
* FunctionalFuzzer file: if the issue is related to running `cats run ...` using the [FunctionalFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/functional-fuzzer/)
* SecurityFuzzer file: if the issue is related to running `cars run ...` using the [SecurityFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/security-fuzzer/)
* Request template: if the issue is related to running `cats fuzz ...` using the [TemplateFuzzer](https://endava.github.io/cats/docs/fuzzers/special-fuzzers/template-fuzzer/)
* User dictionary: if the issue is related to running CATS with a [custom dictionary](https://endava.github.io/cats/docs/getting-started/custom-dictionary)

**Additional context**
Add any other context about the problem here.
