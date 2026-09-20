# Architecture Decision Records

One file per structural decision, numbered and append-only. To reverse a decision, write a new
ADR and mark the old one `Superseded by …` — never rewrite an accepted record.

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-hexagonal-multi-module-layout.md) | Enforce hexagonal architecture with Maven modules | Accepted |
| [0002](0002-use-case-classes-over-input-port-interfaces.md) | Call use-case classes directly instead of input-port interfaces | Accepted |
| [0003](0003-jpa-entities-separate-from-aggregates.md) | Keep JPA entities separate from domain aggregates | Accepted |
| [0004](0004-mapstruct-for-cross-boundary-mapping.md) | Use MapStruct for all cross-boundary mapping | Accepted |
| [0005](0005-shared-rest-error-contract.md) | Share the REST error contract via a subclassed `@RestControllerAdvice` | Accepted |
| [0006](0006-separate-dtos-per-ring.md) | Give each ring its own DTOs | Accepted |

Start a new one from [`0000-template.md`](0000-template.md).
