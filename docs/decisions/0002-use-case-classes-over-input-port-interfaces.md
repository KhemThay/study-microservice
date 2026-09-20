# 0002. Call use-case classes directly instead of input-port interfaces

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

Hexagonal architecture defines ports on both sides. Output (driven) ports are uncontroversial —
`OrderRepository` must be an interface, because the implementation lives in an outer module and
the dependency has to be inverted.

Input (driving) ports are less clear-cut. The textbook form gives every use case an interface:

```java
public interface CreateOrderInputPort {
    CreateOrderResult execute(CreateOrderCommand command);
}
public class CreateOrderUseCase implements CreateOrderInputPort { ... }
```

The controller then depends on the interface. But the interface and its single implementation
both live in `order-application-service`, so no dependency is actually inverted — the controller
already depends on that module either way.

This codebase contains both styles, which forced the question:

- `port/input/ExplicitPort` — an input-port interface, implemented by nothing.
- `usecase/CreateOrderUseCase` — a concrete `@Component`, injected directly into
  `OrderCommandController`.

## Decision

**Output ports are interfaces. Input ports are concrete use-case classes.**

`OrderCommandController` depends on `CreateOrderUseCase` directly. Use cases live in
`mptc.khay.order.domain.usecase` and are annotated `@Component`.

`ExplicitPort` stays for now as a worked example of the alternative, marked as unused.

## Consequences

**Easier**

- One class per use case instead of two. Less indirection when reading the flow.
- Jumping from controller to implementation is a single navigation.
- The class name already states the contract: `CreateOrderUseCase.execute(CreateOrderCommand)`.

**Harder**

- Mocking a use case in a `@WebMvcTest` mocks a class rather than an interface. Mockito handles
  this fine; a strict "mock interfaces only" policy would not.
- If a second implementation is ever needed (a decorator, a feature-flagged variant), an
  interface has to be extracted then.
- Purists will note this is not textbook hexagonal.

**Neutral**

- Nothing about the dependency rule changes. The controller depends inwards either way.

## Notes

`ExplicitPort` is currently dead code and reads as an oversight rather than a deliberate
example. Either implement it or delete it — tracked in
[current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).
