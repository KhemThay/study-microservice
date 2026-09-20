# 0005. Share the REST error contract via a subclassed `@RestControllerAdvice`

- **Status:** Accepted
- **Date:** 2025-09-20

## Context

Every service in this codebase should fail the same way: one JSON envelope, one field-error
shape, so clients write one error parser. That argues for putting
`RestApiErrorResponse`, `FieldErrorResponse` and a `@RestControllerAdvice` in a shared module —
`common-restapi`.

Spring then gets in the way. `@SpringBootApplication` component-scans **its own package and
below**. `OrderServiceApplication` is in `mptc.khay.order`; the shared handler is in
`mptc.khay.restapi.exception`. Spring never sees it, and validation errors fall through to
Spring's default error body.

Three ways out:

1. **Widen the scan** — `@ComponentScan({"mptc.khay.order", "mptc.khay.restapi"})` on the Boot
   class.
2. **Auto-configuration** — ship `common-restapi` with an
   `AutoConfiguration.imports` entry so Boot registers the bean with no scanning.
3. **Subclass it** — declare an empty subclass inside a scanned package.

## Decision

Option 3.

`common-restapi` owns the contract:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestApiErrorResponse<?> handleException(MethodArgumentNotValidException e) { ... }
}
```

and each service makes it scannable and specialises it:

```java
package mptc.khay.order.restapi.exception;

@RestControllerAdvice
public class OrderGlobalExceptionHandler extends GlobalExceptionHandler {
    // order-specific @ExceptionHandler methods go here
}
```

## Consequences

**Easier**

- Every service produces an identical 400 body with no duplicated code.
- The subclass is the obvious, already-existing home for service-specific handlers —
  `OrderDomainException` → 400, not-found → 404. With options 1 and 2 you would need this class
  anyway, so the subclass costs nothing extra.
- No auto-configuration machinery, no `spring.factories`-era indirection, and no widened scan
  that might sweep up other unintended beans from `mptc.khay.*`.

**Harder**

- Every new service must remember to create the subclass. Forget it and errors silently revert
  to Spring's default body — a failure with no error message.
- `@RestControllerAdvice` must be repeated on the subclass; the annotation is not inherited.
- Two classes where the naive reading expects one.

**Currently outstanding**

`OrderGlobalExceptionHandler` is empty except for a `// TODO: write the exception` marker, so
`OrderDomainException` still becomes a bare 500. The subclass exists for exactly this and is
unfinished — see
[current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).

## Notes

If the number of services grows, revisit option 2. An auto-configured handler cannot be
forgotten, which is the main weakness of this decision.
