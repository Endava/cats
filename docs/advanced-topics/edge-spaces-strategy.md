---
sidebar_position: 3
description: How CATS handles data sanitization and trimming
---

# Edge Spaces Strategy
There isn't a consensus on how you should handle situations when you trail or prefix valid values with spaces.
One strategy is to have the service trimming spaces before doing the validation, while some other services will just validate them as they are.
You can control how CATS should expect such cases to be handled by the service using the `--edgeSpacesStrategy` argument.
You can set this to `TRIM_AND_VALIDATE` or `VALIDATE_AND_TRIM` depending on how you expect the service to behave:
- `TRIM_AND_VALIDATE` means that the service will first trim the spaces and after that run the validation
- `VALIDATE_AND_TRIM` means that the service runs the validation first without any trimming of spaces

:::caution
This is a **global setting** i.e. configured when CATS starts and all Fuzzer expect a consistent behaviour from all API endpoints.
:::