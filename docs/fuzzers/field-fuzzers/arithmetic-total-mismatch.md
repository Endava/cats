---
hide_table_of_contents: true
---

# Arithmetic Total Mismatch Fields

| Item | Description |
|:--|:--|
| **Full Fuzzer Name** | `ArithmeticTotalMismatchFieldsFuzzer` |
| **Log Key** | **ATMFF** |
| **Description** | Identifies sibling numeric triples representing financial or quantity calculations (such as `unitPrice * quantity = totalAmount` or `subtotal + taxAmount = grandTotal`) and sends a contradicting total (`98765`) alongside fixed component values (`10` and `2`) to detect backends that persist unvalidated client-calculated totals. |
| **Enabled by default?** | Yes (included in `security`, `ci`, and `full` profiles) |
| **Target field types** | Sibling numeric fields matching price/quantity/total or subtotal/tax/total patterns |
| **Expected result when fuzzed field is required** | `4XX` (or `2XX` if the server ignores the client-supplied total and recomputes it server-side) |
| **Expected result when fuzzed field is optional** | `4XX` (or `2XX` if the server recomputes the total server-side) |
| **Expected result when fuzzed value is not matching field pattern** | `4XX` |
| **Fuzzing logic** | Sets the first component field to `10`, the second component field to `2`, and the total field to `98765`, and inspects whether the response rejects the payload or reflects the tampered total. |
| **Conditions when this fuzzer will be skipped** | When the payload is empty or contains no matching sibling arithmetic field triples. |
| **HTTP methods that will be skipped** | `GET`, `DELETE`, `HEAD`, `TRACE` |
| **Reporting** | Reports `error` (`Inconsistent arithmetic total accepted`) if a `2XX` response reflects the tampered total `98765`, or `Server error` on `5XX`; reports `info` when rejected with `4XX` or recomputed by the server. |
