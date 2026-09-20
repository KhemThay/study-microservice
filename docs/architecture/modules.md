# Module Map

Eleven Maven modules. Four are pure aggregators (`packaging: pom`), the rest produce jars.

```
Spring_study                          (pom)  root: versions, dependencyManagement, compiler plugin
├── common                            (pom)
│   ├── common-domain                 (jar)  DDD building blocks + shared value objects
│   ├── common-restapi                (jar)  shared REST error contract
│   └── common-starter                (jar)  placeholder, empty
└── order-service                     (pom)
    ├── order-service-domain          (pom)
    │   ├── order-domain-core         (jar)  aggregates, entities, events, domain service
    │   └── order-application-service (jar)  use cases, ports, command/result DTOs
    ├── order-service-restapi         (jar)  inbound HTTP adapter
    ├── order-service-persistence     (jar)  outbound JPA adapter
    ├── order-service-messaging       (jar)  outbound event adapter — pom only, no code yet
    └── order-service-main            (jar)  Spring Boot application, composition root
```

---

## Root — `Spring_study`

`pom.xml`, `packaging: pom`. Inherits `spring-boot-starter-parent:4.1.1`, which supplies
managed versions for every Spring/Jakarta dependency so child modules can omit `<version>`.

Three jobs:

1. **Properties** — `java.version=25`, `mapstruct.version=1.6.3`,
   `lombok-mapstruct-binding.version=0.2.0`, `maven-compiler-plugin.version=3.15.0`.
2. **`<dependencyManagement>`** — pins `${project.version}` for every internal module, so a
   child writes `<groupId>mptc.khay</groupId><artifactId>order-domain-core</artifactId>` with
   no version. It also pins `org.mapstruct:mapstruct`.
3. **`<build>`** — the `maven-compiler-plugin` with `release=25` and an explicit
   `annotationProcessorPaths` list (Lombok → lombok-mapstruct-binding → mapstruct-processor),
   configured for both `default-compile` and `default-testCompile`.

It also declares Lombok once as an `<optional>` dependency, which every module inherits.

---

## `common-domain`

The reusable DDD vocabulary. **Zero dependencies.** Plain Java.

| File | Role |
| --- | --- |
| `entity/BaseEntity<ID>` | Abstract base with an `ID` field; `equals`/`hashCode` **by identity only** |
| `entity/AggregateRoot<ID>` | Marker subclass of `BaseEntity` — the only type a repository may load or save |
| `event/DomainEvent<T>` | Marker interface for domain events |
| `exception/DomainException` | Base unchecked domain exception |
| `valueobject/Money` | `record Money(BigDecimal amount)` with `add`, `subtract`, `multiply`, `isGreaterThanZero`, `isGreaterThan`; every result is scaled to 2 d.p. `HALF_EVEN` |
| `valueobject/OrderId`, `CustomerId`, `BusinessId`, `ProductId`, `TrackingId` | Typed identifiers wrapping `UUID` |
| `valueobject/OrderItemId` | Typed identifier wrapping `Integer` (line numbers are local to an order) |
| `valueobject/StreetAddress` | `record(UUID id, String postalcode, String street, String city)` |
| `valueobject/OrderStatus` | `PENDING → PAID → APPROVED`, plus `CANCELLING → CANCELLED` |

`BaseEntity.equals` compares **only the id**, which is correct DDD: two `Order` objects with
the same `OrderId` are the same order regardless of field drift. Value objects are `record`s,
so they get structural equality for free — also correct: two `Money(10.00)` *are* equal.

> There is a leftover IDE-scaffold `Main.java` in this module. It is not used.

---

## `common-restapi`

The shared HTTP error contract, so every service in this codebase fails the same way.

| File | Role |
| --- | --- |
| `dto/RestApiErrorResponse<T>` | `record(String code, String message, T detail)`, Lombok `@Builder` |
| `dto/FieldErrorResponse` | `record(String field, String code, String reason)` |
| `exception/GlobalExceptionHandler` | `@RestControllerAdvice` translating `MethodArgumentNotValidException` → HTTP 400 with the field errors in `detail` |

Depends on `spring-boot-starter-validation`, `spring-boot-starter-webmvc` and `spring-web`.

**Important wiring detail:** this class lives in package `mptc.khay.restapi.exception`, which
is *outside* the `mptc.khay.order` scan root of `OrderServiceApplication`. Spring would never
find it. That is exactly why `order-service-restapi` contains
`OrderGlobalExceptionHandler extends GlobalExceptionHandler`, re-annotated with
`@RestControllerAdvice` inside a scanned package. See
[ADR 0005](../decisions/0005-shared-rest-error-contract.md).

---

## `common-starter`

Declared as a module and built, but contains only a leftover scaffold `Main.java`. It is not
listed in the root `<dependencyManagement>` and nothing depends on it. Intended as the future
home for shared Spring auto-configuration.

---

## `order-domain-core`

The heart. Depends on `common-domain` **and nothing else** — that single-line `pom.xml`
dependency block is what makes the dependency rule a compile error rather than a convention.

| File | Role |
| --- | --- |
| `entity/Order` | The aggregate root. Owns `validateOrder()`, `initializedOrder()`, `pay()`, `approve()`, `initCancel()`, `cancel()` |
| `entity/OrderItem` | Entity inside the `Order` aggregate; `isPriceValid()`, `initializeOrderItem(...)` |
| `entity/Product` | Entity: `ProductId`, `name`, `Money price` |
| `entity/Customer` | Aggregate root: `CustomerId`, `userName`, `givenName`, `familyName` |
| `entity/Business` | Aggregate root: `BusinessId`, `List<Product> products`, `boolean active` |
| `event/OrderEvent` | Abstract `DomainEvent<Order>` carrying the `Order` and a `ZonedDateTime` |
| `event/OrderCreatedEvent`, `OrderPaidEvent`, `OrderCancelledEvent` | Concrete events |
| `exception/OrderDomainException` | `extends DomainException` |
| `service/OrderDomainService` / `OrderDomainServiceImp` | **Empty.** Placeholder for cross-aggregate logic |

Full detail in [domain-model.md](domain-model.md).

---

## `order-application-service`

Use cases and the ports they call. Depends on `order-domain-core`, `spring-context`,
`spring-boot-starter-validation`, `mapstruct`.

| File | Role |
| --- | --- |
| `usecase/CreateOrderUseCase` | `@Component`, `execute(CreateOrderCommand) → CreateOrderResult`. **Currently a stub** that logs and returns a random UUID |
| `port/output/OrderRepository` | `Order saveOrder(Order order)` |
| `port/output/CustomerRepository` | `Optional<Customer> findCustomer(UUID customerID)` |
| `port/output/BusinessRepository` | `Optional<Business> findBusiness(UUID businessId, List<UUID> productIds)` |
| `port/input/ExplicitPort` | `void execute(CreateOrderCommand)` — an example input port, unimplemented |
| `dto/CreateOrderCommand` | `record(UUID customerId, UUID businessId, CommandOrderAddress deliveryAddress, BigDecimal price, List<CommandOrderItem> items)` |
| `dto/CommandOrderItem` | `record(UUID productId, Integer quantity, BigDecimal subtotal, BigDecimal price)` |
| `dto/CommandOrderAddress` | `record(String street, String postalCode, String city)` |
| `dto/CreateOrderResult` | `record(UUID orderId)` |

Note the ports speak **domain types** (`Order`, `Customer`) and plain `UUID`s — never
`OrderEntity` and never `Optional<OrderCreateRequest>`. That asymmetry is the point: the core
dictates the interface, the adapter conforms.

---

## `order-service-restapi`

Inbound adapter. Depends on `order-application-service`, `common-restapi`, `mapstruct`,
`spring-boot-starter-validation`, `spring-web`.

| File | Role |
| --- | --- |
| `controller/OrderCommandController` | `@RestController`, `@RequestMapping("/api/v1/orders")`, `POST` → 201 |
| `mapper/OrderWebMapper` | `@Mapper(componentModel = "spring")` request ⇄ command, result ⇄ response |
| `dtos/OrderCreateRequest` | Validated request body |
| `dtos/OrderAddressRequest`, `OrderItemRequest` | Nested request records with `@NotNull` / `@Size` |
| `dtos/OrderCreateResponse` | `record(UUID orderId)` |
| `exception/OrderGlobalExceptionHandler` | Scannable subclass of the shared handler |

The name `OrderCommandController` implies a CQRS split — a future `OrderQueryController` would
handle reads. Nothing enforces that yet.

> This module's `pom.xml` still carries `start.spring.io` leftovers (a redundant `groupId`,
> `version`, `name`, empty `<licenses>`/`<developers>`/`<scm>` blocks, and an unused
> `org.jetbrains:annotations:13.0`). It also has its own `src/main/resources/application.yaml`
> containing only `spring.application.name`, which is a footgun — see
> [current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).

---

## `order-service-persistence`

Outbound adapter. Depends on `order-application-service`, `spring-boot-starter-data-jpa`,
`mapstruct`.

| File | Role |
| --- | --- |
| `adapter/OrderRepositoryAdapter` | `@Repository implements OrderRepository`. **`saveOrder` returns `null`** — not implemented |
| `adapter/CustomerRepositoryAdapter` | `findById(...).map(mapper::customerEntityToCustomer)` |
| `adapter/BusinessRepositoryAdapter` | Loads rows by business + product ids, folds them into one `Business` |
| `mapper/OrderPersistenceMapper` | MapStruct interface + a `default` method for the rows→aggregate fold |
| `repository/OrderJpaRepository` | `JpaRepository<OrderEntity, UUID>` |
| `repository/CustomerJpaRepository` | `JpaRepository<CustomerEntity, UUID>` |
| `repository/BusinessJpaRepository` | `JpaRepository<BusinessEntity, BusinessIdEntity>` + derived query `findByBusinessIdAndProductIdIn` |
| `entity/OrderEntity` | `orders` — `@OneToMany` items, `@OneToOne` address |
| `entity/OrderItemEntity` | `order_items` — `IDENTITY` id, `@ManyToOne` back to order |
| `entity/OrderAddressEntity` | `order_addresses` — `UUID` id, `@OneToOne(mappedBy="orderAddress")` |
| `entity/CustomerEntity` | `customers` |
| `entity/BusinessEntity` | `businesses` — composite key via `@IdClass(BusinessIdEntity.class)` |
| `entity/BusinessIdEntity` | `Serializable` composite-key class (`businessId` + `productId`) |

The two-layer split — `*JpaRepository` (Spring Data) wrapped by `*RepositoryAdapter` (the port
implementation) — is what keeps `JpaRepository` from leaking into the core. See
[ADR 0003](../decisions/0003-jpa-entities-separate-from-aggregates.md).

---

## `order-service-messaging`

A `pom.xml` depending on `order-application-service`, and nothing else. Reserved for the Kafka
adapter that will publish `OrderCreatedEvent` and friends.

---

## `order-service-main`

The composition root and the only runnable artifact.

| File | Role |
| --- | --- |
| `OrderServiceApplication` | `@SpringBootApplication`, plus `@EntityScan` and `@EnableJpaRepositories` for `mptc.khay.order.persistence` |
| `resources/application.yaml` | Port 8550, PostgreSQL datasource, `ddl-auto: create-drop`, SQL logging |

Depends on all four order modules plus `spring-boot-starter-webmvc` and the PostgreSQL driver
at `runtime` scope.

Because `OrderServiceApplication` sits in `mptc.khay.order`, component scanning already covers
`mptc.khay.order.restapi`, `mptc.khay.order.persistence` and `mptc.khay.order.domain` — the
explicit `@EntityScan` / `@EnableJpaRepositories` are belt-and-braces rather than strictly
required. They would become required if the persistence package were moved outside
`mptc.khay.order`.
