---
sidebar_position: 1
---

# Introduction

CATS is a REST API fuzzer and negative testing tool for OpenAPI endpoints. It helps you run thousands of self-healing API tests within minutes with no coding effort! 

## Overview

CATS is:

- **Comprehensive**: tests are generated automatically based on a large number scenarios and cover **every** field and header
- **Intelligent**: tests are generated based on data types and structural constraints; each Fuzzer has specific expectations depending on the scenario under test
- **Highly Configurable**: high amount of customizations: you can filter specific Fuzzers, HTTP response codes, HTTP methods, request paths, provide business context and a lot more
- **Self-Healing**: as tests are generated, any OpenAPI spec change is picked up automatically
- **Simple to Learn**: flat learning curve, with intuitive configuration and syntax
- **Fast**: automatic process for writing, running and reporting thousands of tests within minutes

By using a simple and minimal syntax, with a flat learning curve, CATS (**C**ontract **A**uto-generated **T**ests for **S**wagger) enables you to generate thousands of API tests within minutes with **no coding effort**.
All tests are **generated, run and reported automatically** based on a pre-defined set of **98 Fuzzers**.
The Fuzzers cover a wide range of boundary testing and negative scenarios from fully random large Unicode values to well crafted, context dependant values based on the request data types and constraints.
Even more, you can leverage the fact that CATS generates request payloads dynamically and write simple end-to-end functional tests.

## Fast Track ⏱️

Get started by running CATS in `blackbox` mode.

Make sure you have CATS installed following the instructions on the Installation page.

### What you'll need

- the OpenAPI spec file for the API you want to test (`openapi.yml`)
- connectivity to the API (you should be able to access (`https://api-url.com`)
- credentials to access the API (`$token`)

You can now run CATS using the following command:

```bash
cats --contract=openapi.yml -H "Authorization=$token" --server=https://api-url.com -b
```

When running in blackbox mode CATS will only report `500` http response codes as errors. 

The report is available at `cats-report/index.html`.
