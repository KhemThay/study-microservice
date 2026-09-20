# Conventions

The patterns actually used in this codebase. Follow them when adding code so the next module
looks like the last one.

## Packages

| Ring | Package root | Example |
| --- | --- | --- |
| Shared domain | `mptc.khay.domain` | `mptc.khay.domain.valueobject.Money` |
| Shared REST | `mptc.khay.restapi` | `mptc.khay.restapi.exception.GlobalExceptionHandler` |
| Order domain core | `mptc.khay.domain` | `mptc.khay.domain.entity.Order` |
| Order application | `mptc.khay.order.domain` | `mptc.khay.order.domain.usecase.CreateOrderUseCase` |
| Order REST adapter | `mptc.khay.order.restapi` | `mptc.khay.order.restapi.controller.OrderCommandController` |
| Order persistence | `mptc.khay.order.persistence` | `mptc.khay.order.persistence.adapter.OrderRepositoryAdapter` |
| Boot app | `mptc.khay.order` | `mptc.khay.order.OrderServiceApplication` |

Everything Spring must scan lives under `mptc.khay.order`, which is the package of
`OrderServiceApplication`. Anything outside it (like `mptc.khay.restapi`) needs a subclass or an
explicit `@ComponentScan` to be found.

> Note the wart: `order-domain-core` uses `mptc.khay.domain.entity`, the same package root as
> `common-domain`. Split packages across jars work on the classpath but would break under JPMS,
> and they make it harder to see at a glance which module a class is in. `mptc.khay.order.domain.entity`
> would be the cleaner choice.

Sub-package by **role**, not by feature: `entity`, `valueobject`, `event`, `exception`,
`service`, `usecase`, `port.input`, `port.output`, `dto`/`dtos`, `controller`, `mapper`,
`adapter`, `repository`.

## Naming

| Thing | Pattern | Example |
| --- | --- | --- |
| Aggregate / entity | Domain noun, no suffix | `Order`, `OrderItem`, `Product` |
| Value object | Domain noun, `record` | `Money`, `OrderId`, `StreetAddress` |
| Domain event | `<Aggregate><PastTenseVerb>Event` | `OrderCreatedEvent`, `OrderPaidEvent` |
| Domain exception | `<Context>DomainException` | `OrderDomainException` |
| Use case | `<Verb><Noun>UseCase` | `CreateOrderUseCase` |
| Application input DTO | `<Verb><Noun>Command` | `CreateOrderCommand` |
| Application output DTO | `<Verb><Noun>Result` | `CreateOrderResult` |
| Nested command DTO | `Command<Noun>` | `CommandOrderItem`, `CommandOrderAddress` |
| Output port | Domain noun + `Repository` | `OrderRepository` |
| Port implementation | `<Port>Adapter` | `OrderRepositoryAdapter` |
| Spring Data interface | `<Noun>JpaRepository` | `OrderJpaRepository` |
| JPA entity | `<Noun>Entity` | `OrderEntity`, `BusinessIdEntity` |
| Web request/response | `<Noun>Request` / `<Noun>Response` | `OrderCreateRequest` |
| Controller | `<Noun>CommandController` / `<Noun>QueryController` | `OrderCommandController` |
| Mapper | `<Context><Ring>Mapper` | `OrderWebMapper`, `OrderPersistenceMapper` |

The `Command`/`Result` vs. `Request`/`Response` split is load-bearing: `Request` means HTTP and
belongs to the adapter; `Command` is transport-agnostic and belongs to the core. Renaming the
application DTOs from `…Request`/`…Response` to `…Command`/`…Result` was a real refactor in this
project's history.

## Domain classes

**Immutable where possible, builder to construct:**

```java
public class Product extends BaseEntity<ProductId> {
    private final String name;
    private final Money price;

    private Product(Builder builder) {
        super.setId(builder.id);
        name = builder.name;
        price = builder.price;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private ProductId id;
        private String name;
        private Money price;
        private Builder() { }
        public Builder id(ProductId val)   { id = val; return this; }
        public Builder name(String val)    { name = val; return this; }
        public Builder price(Money val)    { price = val; return this; }
        public Product build()             { return new Product(this); }
    }
}
```

- Fields `final` unless the lifecycle genuinely changes them.
- Private constructor taking the builder.
- Getters only. **No setters** — except `BaseEntity.setId`, used by the aggregate itself.
- Hand-written builders, not Lombok `@Builder`, so the domain has no Lombok dependency and the
  private-constructor contract is explicit.

> Put `builder()` on the **outer class** (`Product.builder()`). `Order` and `OrderItem`
> currently put it inside `Builder` (`Order.Builder.builder()`); that is the odd one out.

**Value objects are records**, and behaviour goes on them where it belongs (`Money.add`), not in
a `MoneyUtils`.

**Mutation is always a named business operation:**

```java
public void pay() {
    if (orderStatus != OrderStatus.PENDING)
        throw new OrderDomainException("Order is not in correct state for pay operation");
    orderStatus = OrderStatus.PAID;
}
```

Never `setOrderStatus`. Guard the precondition, then mutate.

## Spring in adapters

**Constructor injection via Lombok, never field injection:**

```java
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryAdapter implements CustomerRepository {
    private final CustomerJpaRepository customerJpaRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;
}
```

`final` fields + `@RequiredArgsConstructor`. No `@Autowired` anywhere in this codebase.

**Stereotypes by role:** `@RestController` for controllers, `@Repository` for port adapters,
`@Component` for use cases. (`@Service` is unused here; `@Component` on a use case is fine and
avoids implying "service layer".)

## DTOs

All DTOs are `record`s. Web DTOs also carry Lombok `@Builder`, which is convenient for building
fixtures in tests.

Validation annotations belong on the **web** DTOs only — `@NotNull`, `@Size`, and `@Valid` to
cascade into nested records and list elements. Command DTOs carry no annotations: by the time a
command exists, structural validation has already passed.

Commands carry primitives (`UUID`, `BigDecimal`), not value objects. Lifting `BigDecimal` into
`Money` is the use case's job.

## Mappers

```java
@Mapper(componentModel = "spring")
public interface OrderWebMapper { ... }
```

- One mapper interface per boundary, named `<Context><Ring>Mapper`.
- Method names spell out both sides: `orderCreateRequestToCreateOrderCommand`,
  `businessEntityToProduct`.
- Use `@Mapping(source = "...", target = "...")` for any name mismatch, and dotted targets
  (`target = "id.value"`) to construct value objects.
- Drop to a `default` method when the shapes genuinely differ — e.g. folding `List<BusinessEntity>`
  into one `Business`. Comment *why* the shapes differ.

**Recommended (not yet applied):** add `unmappedTargetPolicy = ReportingPolicy.ERROR` so a
rename turns into a build failure instead of a silent `null`.

## POMs

- Internal dependencies omit `<version>` — the root `dependencyManagement` supplies it.
- Aggregator modules are `packaging: pom` with no `src/`.
- A dependency is declared in the **narrowest** module that needs it. The PostgreSQL driver
  appears only in `order-service-main`, at `runtime` scope.
- No `<version>`, `<name>`, `<licenses>`, `<developers>` or `<scm>` blocks in child POMs — they
  are inherited. (`order-service-restapi/pom.xml` still has these `start.spring.io` leftovers.)

## Configuration

**Exactly one `application.yaml`, in `order-service-main`.** Adapter modules must not ship
their own — a second copy on the classpath shadows the first in a way that is hard to diagnose.

## Comments

The codebase comments sparingly, and only for *why*, not *what*:

```java
// The businesses table has one row per product, so every row carries the same business id and active flag
default Business businessEntitiesToBusiness(List<BusinessEntity> businessEntities) { ... }
```

Section banners like `// =====================Business logic=============//` inside `Order`
mark the boundary between rules and plumbing. Mark real unfinished work with `TODO:` so it is
greppable.
