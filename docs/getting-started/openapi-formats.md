---
sidebar_position: 8
description: Formats recognized by CATS generators
---

# OpenAPI Formats
CATS has custom generators for the most common OpenAPI formats like `date-time`, `email`, `binary` and extends it with a lot more others so that it can generate
data as meaningful as possible. Below you can find the mapping between the values you can use in the `format` field and what CATS will generate.
This will also be very helpful for the consumers of your API, not only for CATS. Additionally, CATS might infer the data format from the property name.
This is explicitly mentioned in the mapping table.

| OpenAPI format              | Property Name                                                             | What CATS generates                    |
|-----------------------------|---------------------------------------------------------------------------|----------------------------------------|
| `bcp47`                     | N/A                                                                       | `en-US`                                | 
| `byte` or `binary`          | N/A                                                                       | base64 encoded string                  |
| `cardNumber`                | or name ends with`cardNumber`                                             | `4111111111111111`                     |
| `iso3166alpha2`             | N/A                                                                       | `US`                                   |
| `iso3166alpha3`             | N/A                                                                       | `USA`                                  |
| `iso-3166` or `ountryCode`  | or name ends with `countryCode`                                           | `USA`                                  |
| `iso-4217` or`currencyCode` | or name ends with `currencyCode`                                          | `USD`                                  |
| `date`                      | N/A                                                                       | `2022-10-25`                           |
| `date-time`                 | N/A                                                                       | `2011-12-03T10:15:30Z`                 |
| `duration`                  | N/A                                                                       | `P1DT30H4S`                            |
| `email`                     | or name ends with `email`or `emailAddress`                                | `cool@cats.io`                         |
| `ean8` or `gtin8`           | N/A                                                                       | `40170725`                             |
| `gtin13` or `ean13`         | or `europeanAricleNumber`, `globalTradeItemNumber` or `globalTradeNumber` | `5710798389878`                        | 
| `hostname`                  | N/A                                                                       | `www.endava.com`                       | 
| `idn-email`                 | N/A                                                                       | `cööl.cats@cats.io`                    | 
| `idn-hostname`              | N/A                                                                       | `www.ëndava.com`                       | 
| `ip` or `ipv4`              | or name ends with `ip` or `ipAddress`                                     | `10.10.10.20`                          |
| `ipv6 `                     | or name ends with `ipv6`                                                  | `21DA:D3:0:2F3B:2AA:FF:FE28:9C5A`      |
| `iri`                       | N/A                                                                       | `http://ëxample.com/cats`              | 
| `iri-reference`             | N/A                                                                       | `/füzzing/`                            |
| `isbn` or `isbn10`          | or name equals `isbn` or `isbn10`                                         | `0439023481`                           |
| `isbn13`                    | or name equals `isbn13`                                                   | `9780439023481`                        |
| `json-pointer`              | N/A                                                                       | `/item/0/id`                           |
| `password`                  | N/A                                                                       | `catsISc00l?!useIt#`                   |
| `period`                    | N/A                                                                       | `P1DT30H4S`                            |
| `regex`                     | N/A                                                                       | `[a-z0-9]+`                            |
| `relative-json-pointer`     | N/A                                                                       | `1/id`                                 |
| `time`                      | N/A                                                                       | `10:15:30Z`                            |
| `uri` or `url`              | or name equals or ends with `url` or `uri`                                | `http://example.com/cats`              |
| `uri-reference`             | N/A                                                                       | `/fuzzing/`                            |
| `uri-template`              | N/A                                                                       | `/fuzzing/{path}`                      |
| `uuid`                      | N/A                                                                       | `c58919de-3210-4549-87fa-c196324d0594` |


:::info
Whenever you see a `camelCase` naming, CATS also checks for `snake_case` and `kebab-case`. For example, for `countryCode` CATS will also match properties ending in `country-code` and `country_code`. 
:::