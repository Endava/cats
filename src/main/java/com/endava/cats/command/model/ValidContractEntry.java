package com.endava.cats.command.model;

import java.util.List;

/**
 * Entity to hold OpenAPI spec validation information.
 *
 * @param valid   if the OpenAPI contract is valid or not
 * @param version the OpenAPI version
 * @param reasons if spec is not valid, this will hold the reasons why
 */
public record ValidContractEntry(boolean valid, String version, List<String> reasons) {
}
