---
sidebar_position: 20
description: Using examples from OpenAPI spec vs Generated examples
---

# Examples

CATS doesn't need examples to generate tests, but they can be useful for understanding the API better. In case your OpenAPI spec has examples, CATS will use them to generate tests.
You can control how examples are being used with the following arguments:

- `--useRequestBodyExamples` - uses the examples from the request body that are set at the media type level like the below example. Default value is `false`.

```yaml
paths:
  /users:
    post:
      summary: Adds a new user
      requestBody:
        content:
          application/json: # Media type
            schema: # Request body contents
              $ref: "#/components/schemas/User" # Reference to an object
            examples: # Child of media type
              Jessica: # Example 1
                value:
                  id: 10
                  name: Jessica Smith
              Ron: # Example 2
                value:
                  id: 11
                  name: Ron Stewart
```

- `--useResponseBodyExamples` - uses the examples from the response body that are set at the media type level like the below example. Default value is `true`.

```yaml
responses:
  "200":
    description: A user object.
    content:
      application/json:
        schema:
          $ref: "#/components/schemas/User" # Reference to an object
        examples:
          Jessica:
            value:
              id: 10
              name: Jessica Smith
          Ron:
            value:
              id: 20
              name: Ron Stewart
```

-- `--useSchemaExamples` - uses the examples from the schema that are set at the schema level like the below example. Default value is `false`.

```yaml
components:
  schemas:
    User: # Schema name
      type: object
      properties:
        id:
          type: integer
        name:
          type: string
      example: # Object-level example
        id: 1
        name: Jessica Smith
```

-- `--usePropertyExamples` - uses the examples from the schema that are set at the property level like the below example. Default value is `true`.

```yaml
components:
  schemas:
    User: # Schema name
      type: object
      properties:
        id:
          type: integer
          example: 1
        name:
          type: string
          example: Jessica Smith
```

-- `--useExamples` - equivalent to setting all above arguments to `true`. Default value is `false`. When provide it will use all examples from the OpenAPI spec.

# CATS Generated values
If examples are not provided CATS will generate values based on the schema definition. For example, if the schema defines a property as `type: integer` and `format: int32` CATS will generate a random integer value between -2147483648 and 2147483647.
It also has build in generators that are able to generate valid names, addresses, emails, etc. based on the property name or format type.