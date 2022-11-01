# Fuzzers

To get a list of fuzzers and a short description run `cats list --fuzzers`.

There are multiple categories of Fuzzers available:

- `Field` Fuzzers which target request body fields or path parameters
- `Header` Fuzzers which target HTTP headers
- `HTTP` Fuzzers which target just the interaction with the service (without fuzzing fields or headers)

Additional checks which are not actually using any fuzzing, but leverage the CATS internal model for consistency and are also called Fuzzers:

- `ContractInfo` Fuzzers which checks the contract for API good practices
- `Special` Fuzzers a special category which need further configuration and are focused on more complex activities like functional flow, custom dictionaries or supplying your own request templates, rather than OpenAPI specs.

```mdx-code-block
import DocCardList from '@theme/DocCardList';

<DocCardList />
```