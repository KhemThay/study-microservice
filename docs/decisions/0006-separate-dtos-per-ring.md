# 0006. Give each ring its own DTOs

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

Creating an order involves the same data in four shapes:

```
OrderCreateRequest → CreateOrderCommand → Order (aggregate) → OrderEntity
```

The obvious objection is that this is the same fields written four times, with mappers between
them. A single shared DTO would be far less code.

But each shape answers to a different owner and changes for a different reason:

| Shape | Module | Answers to | Changes when |
| --- | --- | --- | --- |
| `OrderCreateRequest` | restapi | HTTP clients | the public API changes |
| `CreateOrderCommand` | application-service | the use case | the business operation changes |
| `Order` | domain-core | the business | the rules change |
| `OrderEntity` | persistence | the DBA | the schema changes |

Share one class and those four rate-of-change curves get welded together. A renamed JSON field
becomes a schema migration; a Jackson annotation ends up on the aggregate; adding a column
changes the public API.

## Decision

Each ring owns its own types, and mappers sit at the seams.

**Web (`order-service-restapi`)** — `record`s with Lombok `@Builder` and Jakarta validation:
`OrderCreateRequest`, `OrderAddressRequest`, `OrderItemRequest`, `OrderCreateResponse`.

**Application (`order-application-service`)** — plain `record`s, no annotations, primitives
only: `CreateOrderCommand`, `CommandOrderAddress`, `CommandOrderItem`, `CreateOrderResult`.

**Domain (`order-domain-core`)** — aggregates and value objects with behaviour and invariants.

**Persistence (`order-service-persistence`)** — JPA `@Entity` classes, per
[ADR 0003](0003-jpa-entities-separate-from-aggregates.md).

Two naming rules follow:

- Application DTOs are **`Command` / `Result`**, never `Request` / `Response`. `Request` implies
  HTTP; a command must stay meaningful when the caller is a Kafka consumer or a scheduled job.
  (The DTOs here were renamed from `CreateOrderRequest`/`CreateOrderResponse` for exactly this
  reason.)
- Commands carry **primitives** (`UUID`, `BigDecimal`), not value objects. Lifting `BigDecimal`
  into `Money` is the use case's job, on the way in.

## Consequences

**Easier**

- The API can be versioned (`/api/v1/`) without touching the domain. A `v2` request shape is a
  new DTO plus a mapper method.
- Validation annotations stay at the edge. The domain has no `jakarta.validation` dependency and
  its rules are expressed as code, not annotations.
- Jackson never sees an aggregate, so no `@JsonIgnore` creeps into the domain and no internal
  field is accidentally exposed.
- The core is callable from a non-HTTP entry point unchanged.

**Harder**

- Four shapes, roughly the same fields. Adding one field means touching several files.
- Mapping bugs become possible — and this project has two, both silent `null`s from name
  mismatches. See [ADR 0004](0004-mapstruct-for-cross-boundary-mapping.md) and
  [current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).
- More classes to navigate for anyone new to the codebase.

**Mitigation**

MapStruct generates the mapping code, and `unmappedTargetPolicy = ReportingPolicy.ERROR` turns
a mismatch into a build failure rather than a runtime `null`.
