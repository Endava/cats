---
sidebar_position: 1
description: How CATS intelligently handles DELETEs
---

# DELETE Requests
`DELETE` is the only HTTP verb that is intended to remove resources. Executing the same `DELETE` request twice will result in the second one to fail as the resource is no longer available.
It will be pretty heavy to supply a large list of identifiers within the `--refData` file and this is why the initial recommendation was to skip the `DELETE` method when running CATS.

Starting with version 7.0.2 CATS has some intelligence in dealing with `DELETE`. In order to have enough valid entities CATS will save the corresponding `POST` requests in an internal Queue, and
everytime a `DELETE` request is executed it will poll data from there. In order to have this actually working, your contract must comply with common sense conventions:

- the `DELETE` path is actually the `POST` path plus an identifier: if POST is `/pets`, then DELETE is expected to be `/pets/{petId}`.
- CATS will try to match the `{petId}` parameter within the body returned by the `POST` request while doing various combinations of the `petId` name. It will try to search for the following entries: `petId, id, pet-id, pet_id` with different cases.
- If any of those entries is found within a stored `POST` result, it will replace the `{petId}` with that value

For example, suppose that a POST to `/pets` responds with:

```json
{
  "pet_id": 2,
  "name": "Chuck"
}
```

When doing a `DELETE` request, CATS will discover that `{petId}` and `pet_id` are used as identifiers for the `Pet` resource, and will do the `DELETE` at `/pets/2`.

Starting with CATS 14.1.0 and 15.0.0, [Runtime Resource Reuse](./runtime-resource-reuse) (`--reuseSuccessfulResources`, enabled by default) generalizes this behavior:
- CATS captures resource identifiers from `2XX` `POST`, `PUT`, `PATCH`, and `GET` responses (including `Location` headers and nested collections) in the `RuntimeResourcePool`.
- During the initial seed wave, `HappyPath` `DELETE` requests are postponed until all paths have been fuzzed, and then executed in reverse path order (child paths before parent paths) so that created resources remain available to `GET`, `PUT`, `PATCH`, and child operations throughout the run.
- Once a `DELETE` succeeds (`2XX`), its identifier is evicted from the pool and [`CheckDeletedResourcesNotAvailableFuzzer`](../fuzzers/http-fuzzers/check-deleted) verifies that a subsequent `GET` to the deleted URL returns `404` or `410`.

If these conventions are followed (which also align to good REST naming practices), it is expected that `DELETE` and `POST` requests will be on-par for most of the resources.

:::caution
If your contract does not follow the above conventions you will need to supply resource identifiers using `--refData` or `--urlParams`. 
As `DELETE` makes resources unavailable, you can also skip `DELETE` or supply the exact HTTP methods you want to test using `--httpMethods`.
:::
