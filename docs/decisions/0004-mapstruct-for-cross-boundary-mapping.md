# 0004. Use MapStruct for all cross-boundary mapping

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

[ADR 0003](0003-jpa-entities-separate-from-aggregates.md) and
[ADR 0006](0006-separate-dtos-per-ring.md) mean data is reshaped at three boundaries:
request → command, command → aggregate, aggregate → entity (and back). Something has to do that
translation. The options:

1. **Hand-written mappers** — explicit and debuggable, but hundreds of lines of
   `new X(y.getA(), y.getB(), ...)` that must be updated on every field change.
2. **Reflection-based mappers** (ModelMapper, Dozer) — no code, but runtime cost, runtime
   failures, and no help from the compiler.
3. **Annotation-processor mappers** (MapStruct) — generate plain Java at compile time.

## Decision

Use **MapStruct 1.6.3** with `componentModel = "spring"` for every boundary.

One mapper interface per boundary:

- `OrderWebMapper` — `OrderCreateRequest` ⇄ `CreateOrderCommand`, `CreateOrderResult` →
  `OrderCreateResponse`
- `OrderPersistenceMapper` — JPA entities ⇄ domain aggregates

`componentModel = "spring"` makes the generated `*Impl` a `@Component`, so it is injected like
any other bean. Use `@Mapping` for name mismatches and dotted targets (`target = "id.value"`) to
construct value objects; drop to a `default` method when the shapes genuinely differ.

The processor chain is configured once in the root POM:

```xml
<annotationProcessorPaths>
  <path>…lombok…</path>
  <path>…lombok-mapstruct-binding…</path>
  <path>…mapstruct-processor…</path>
</annotationProcessorPaths>
```

for both `default-compile` and `default-testCompile`.

## Consequences

**Easier**

- Mapping code is generated, not maintained. Adding a field to both sides needs no mapper edit.
- Zero reflection at runtime; the generated code is ordinary getters and constructors.
- Type errors are compile errors.
- Nested records and lists are handled automatically — `List<OrderItemRequest>` →
  `List<CommandOrderItem>` needs no configuration.
- MapStruct can construct value objects from primitives (`target = "price.amount"` builds a
  `Money`), which keeps the lifting out of hand-written code.

**Harder**

- **Silent unmapped fields.** MapStruct matches by name, case-sensitively, and the default
  `unmappedTargetPolicy` is `WARN`. A rename compiles clean and yields `null` at runtime. This
  project already has two such bugs (`orderAddress`→`deliveryAddress`,
  `subTotal`→`subtotal`).
- Debugging means reading generated sources in `target/generated-sources/annotations/`.
- Annotation-processor ordering with Lombok is fragile enough to need
  `lombok-mapstruct-binding` and an explicit `annotationProcessorPaths` list. Once you declare
  that list you must name *every* processor, Lombok included.
- Renaming a field in an IDE will not update a `@Mapping(source = "...")` string.

**Required follow-up**

Set `unmappedTargetPolicy = ReportingPolicy.ERROR` on every `@Mapper`. The main drawback is
fully mitigated by one attribute, and without it the tool trades compile-time safety for silent
data loss — the opposite of why it was chosen.
