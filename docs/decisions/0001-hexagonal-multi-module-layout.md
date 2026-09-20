# 0001. Enforce hexagonal architecture with Maven modules

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

The core value of hexagonal architecture is that business rules do not depend on frameworks.
The usual way to express that is a package convention — `domain`, `application`,
`infrastructure` — inside one Maven module.

A convention held by agreement alone decays. In a single module, `order-domain-core` classes
have Spring, Hibernate and Jackson on the compile classpath, so nothing stops someone adding
`@Entity` to the `Order` aggregate "just for now". The violation is invisible until someone
reads the file.

This is a study project whose *purpose* is to learn the architecture, so the boundaries need to
be unmistakable.

## Decision

Split each ring into its own Maven module and let the module graph enforce the dependency rule.

```
main ──> restapi ──────> application-service ──> domain-core ──> common-domain
 │   └─> persistence ──┘
 └─────> messaging ────┘
```

Concretely:

- `order-domain-core` declares **exactly one** dependency: `common-domain`.
- `common-domain` declares **none**.
- `order-application-service` may add `spring-context` (for `@Component`) and MapStruct, but
  nothing transport- or database-specific.
- Adapter modules depend on `order-application-service`; it never depends on them.
- `order-service-main` is the only module that depends on everything.

Shared, service-agnostic code goes under `common/` so a future `payment-service` can reuse it.

## Consequences

**Easier**

- A dependency-rule violation is a **compile error**. `import jakarta.persistence.Entity` in an
  aggregate does not resolve, because JPA is not on that module's classpath.
- The domain is testable with plain JUnit — no Spring context, no database, milliseconds.
- Swapping an adapter means editing one `pom.xml` in `order-service-main`.
- The module list *is* the architecture diagram.

**Harder**

- Eleven modules for one service. Significant ceremony for a small codebase.
- A cross-cutting change touches several POMs.
- IDE and build times grow; `mvn install` (not `package`) is required so siblings resolve.
- Three sets of near-identical DTOs and the mappers between them — see
  [0006](0006-separate-dtos-per-ring.md).

**Mitigations**

- Root `dependencyManagement` pins `${project.version}` once, so child POMs omit versions.
- Modules are named `<service>-<ring>`, so a name states which ring it is in.

## Notes

The trade-off is explicitly weighted towards teaching. For a small production service, the same
rule could be enforced inside one module with ArchUnit tests, at a fraction of the ceremony.
