---
sidebar_position: 92
description: Detect duplicate values in unique arrays
---

# DuplicateItemsInUniqueArraysFields

This fuzzer targets arrays declared with `uniqueItems: true`. It replaces the
array with a value containing a duplicate item and checks whether the service
rejects the invalid request instead of silently accepting duplicate values.

Select it with:

```bash
cats -c openapi.yml -s http://localhost:8080 \
  --fuzzers=DuplicateItemsInUniqueArraysFields
```
