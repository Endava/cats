---
sidebar_position: 11
description: How to supply custom path parameters
---

# URL Parameters
There are cases when certain parts of the request URL are parameterized but have fixed values. For example a case like: `/{version}/pets`. `{version}` is supposed to have the same value for all requests.
You can replace such parameters using the `--urlParams` argument.
You can supply a `,` separated list of `name:value` pairs to replace the `name` parameters with their corresponding `value`.
For example, supplying `--urlParams=version:v1.0` will replace the `version` parameter from the above example with `v1.0`.

:::note
You can achieve a similar behaviour using `--refData` and set the reference value for all paths using `all`.
:::
