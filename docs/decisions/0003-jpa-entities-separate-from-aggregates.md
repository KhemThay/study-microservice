# 0003. Keep JPA entities separate from domain aggregates

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

The fastest way to build a Spring app is to annotate the domain model with JPA and use it
everywhere. One `Order` class, `@Entity`, returned straight from a `JpaRepository`.

That convenience has a price, and the two models want opposite things:

| JPA requires | DDD wants |
| --- | --- |
| No-arg constructor | Construction only through a valid state |
| Mutable fields / setters | `final` fields, mutation only via business operations |
| Flat, column-shaped types | Value objects (`Money`, `OrderId`, `StreetAddress`) |
| Whatever the schema says | Whatever the business says |

Let JPA win and the aggregate loses its invariants: anyone can `new Order()` and
`setOrderStatus(APPROVED)`. Let DDD win and Hibernate cannot instantiate the class. The deeper
cost is that the schema then dictates the model — a denormalised table becomes a denormalised
aggregate.

## Decision

Maintain two models with a mapper between them.

- **Domain**, in `order-domain-core`: `Order`, `OrderItem`, `Product`, `Customer`, `Business` —
  plain Java, `final` fields, builders, value objects, no annotations.
- **Persistence**, in `order-service-persistence`: `OrderEntity`, `OrderItemEntity`,
  `OrderAddressEntity`, `CustomerEntity`, `BusinessEntity` — `@Entity`, Lombok
  `@Getter @Setter @NoArgsConstructor`, flat primitive columns.
- **`OrderPersistenceMapper`** (MapStruct) translates between them.
- **`*RepositoryAdapter`** classes implement the core's ports and return domain types only.
  `JpaRepository` never leaves the persistence module.

## Consequences

**Easier**

- The aggregate keeps its invariants: no setters, no no-arg constructor, no way into an invalid
  state.
- The schema can be denormalised for query performance without deforming the domain. The
  `businesses` table is one row per (business, product) pair and folds into a single `Business`
  aggregate inside the mapper.
- The domain has no Hibernate on its classpath at all, so no lazy-loading surprises, no proxies,
  no `LazyInitializationException` leaking into business code.
- Changing database technology touches one module.

**Harder**

- Every field exists twice and must be mapped. MapStruct absorbs most of it, but mismatches are
  real — this project has two silent `null`s from name mismatches, see
  [current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).
- Non-trivial mappings (`List<String> failureMessages` ⇄ `String`, `StreetAddress` ⇄
  `OrderAddressEntity`) have to be written by hand.
- Two id-generation strategies can disagree: `OrderEntity.orderId` is
  `@GeneratedValue(UUID)` while `Order.initializedOrder()` already assigns its own `OrderId`.
  The domain should win; the `@GeneratedValue` should go.
- No dirty-checking of the aggregate. The adapter must map the whole thing and save explicitly.

## Notes

Mitigate the mapping risk with `unmappedTargetPolicy = ReportingPolicy.ERROR` on every
`@Mapper`, which turns a silent `null` into a build failure.
