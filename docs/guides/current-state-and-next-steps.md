# Current State & Next Steps

An honest inventory of what works, what is scaffolding, and what is broken — as of the
`persistence_restapi_adapter` branch.

## Status at a glance

| Area | State |
| --- | --- |
| Module structure & dependency rule | ✅ Complete and correct |
| Domain model (`Order`, `OrderItem`, `Money`, VOs, events) | ✅ Implemented, untested |
| `OrderDomainService` | ⚠️ Empty interface + empty impl |
| `CreateOrderUseCase` | ⚠️ Stub — logs, returns a random UUID |
| Output ports | ✅ Declared |
| `CustomerRepositoryAdapter`, `BusinessRepositoryAdapter` | ✅ Implemented |
| `OrderRepositoryAdapter.saveOrder` | ❌ Returns `null` |
| `Order ⇄ OrderEntity` mapping | ❌ Does not exist |
| REST controller + DTOs + validation | ✅ Working |
| `OrderWebMapper` | ⚠️ Compiles, but drops two fields |
| Error handling | ⚠️ Validation only; domain exceptions → 500 |
| Messaging / events | ❌ Empty module, nothing published |
| Tests | ❌ None |
| Database migrations | ❌ `ddl-auto: create-drop` only |

---

## Bugs

These are defects in existing code, not missing features.

### 1. `OrderWebMapper` silently drops `deliveryAddress`

`OrderCreateRequest.orderAddress` vs. `CreateOrderCommand.deliveryAddress`. MapStruct matches by
name, finds no match, and leaves the target `null`. Confirmed in the generated
`OrderWebMapperImpl`:

```java
CommandOrderAddress deliveryAddress = null;
CreateOrderCommand createOrderCommand =
        new CreateOrderCommand(customerId, businessId, deliveryAddress, price, items);
```

Every order created through the API has no delivery address.

```java
@Mapping(source = "orderAddress", target = "deliveryAddress")
CreateOrderCommand orderCreateRequestToCreateOrderCommand(OrderCreateRequest orderCreateRequest);
```

### 2. `OrderWebMapper` silently drops every item's subtotal

`OrderItemRequest.subTotal` vs. `CommandOrderItem.subtotal` — capital `T` against lowercase.
Same failure mode; `OrderWebMapperImpl` hardcodes `BigDecimal subtotal = null;`.

Fix by renaming `CommandOrderItem.subtotal` → `subTotal` (consistent with the rest of the
codebase) or adding an explicit `@Mapping`.

### 3. Both bugs can be made impossible

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderWebMapper { ... }
```

An unmapped target then fails the build instead of producing `null`. Worth applying to
`OrderPersistenceMapper` too. **Do this first** — it will surface anything similar hiding
elsewhere.

### 4. `Order.validateItemsPrice` throws the wrong exception type

```java
throw new RuntimeException("Total price: " + ... );
```

Every other check throws `OrderDomainException`. A bare `RuntimeException` escapes any
`@ExceptionHandler(OrderDomainException.class)` and becomes a 500.

### 5. `updateFailureMessages` can never record a message

```java
public void initCancel() {
    ...
    updateFailureMessages(failureMessages);   // passes the field to itself
}
```

Both `initCancel()` and `cancel()` pass their own field as the argument, so
`this.failureMessages.addAll(this.failureMessages)` is the best case. They should take a
`List<String> failureMessages` parameter from the caller.

### 6. `Money.equals` is scale-sensitive

`Money` is a record, so `equals` uses `BigDecimal.equals`, which compares scale as well as
value: `10.0` ≠ `10.00`. Results of `add`/`multiply` are always scale-2, but a `Money` built
from a JSON payload has whatever scale the client sent. `Order.validateItemsPrice()` and
`OrderItem.isPriceValid()` compare exactly those two things, so a client sending `"price": 50`
fails validation against a computed `50.00`.

Fix by normalising on construction:

```java
public record Money(BigDecimal amount) {
    public Money {
        amount = amount == null ? null : amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}
```

A compact constructor also guarantees every `Money` in the system is scale-2, wherever it came
from.

### 7. `OrderEntity.orderStatus` is persisted as an ordinal

No `@Enumerated(EnumType.STRING)`, so JPA stores the enum's ordinal position. Reordering
`OrderStatus` silently corrupts existing rows.

```java
@Enumerated(EnumType.STRING)
private OrderStatus orderStatus;
```

### 8. `OrderEntity` relations do not cascade

```java
@OneToMany(mappedBy = "order")
private List<OrderItemEntity> items;

@OneToOne
private OrderAddressEntity orderAddress;
```

No `cascade` and no `orphanRemoval`, so saving an `OrderEntity` will not save its items or
address. Saving an aggregate must save the whole aggregate:

```java
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
@OneToOne(cascade = CascadeType.ALL)
```

### 9. Two `application.yaml` files on the classpath

`order-service-restapi` ships one containing `spring.application.name: order-service-restapi`,
and `order-service-main` ships the real one. Only one is loaded, and which one depends on
classpath ordering. Delete the adapter's copy — configuration belongs to the composition root.

---

## Missing features, in build order

### Step 1 — Implement `OrderDomainService`

The linchpin: nothing downstream can be finished without it.

```java
public interface OrderDomainService {
    OrderCreatedEvent validateAndInitiateOrder(Order order, Business business);
    OrderPaidEvent payOrder(Order order);
    OrderCancelledEvent cancelOrderPayment(Order order, List<String> failureMessages);
}
```

`validateAndInitiateOrder` should check `business.isActive()`, attach catalogue prices to each
`OrderItem`'s `Product`, then call `order.validateOrder()` and `order.initializedOrder()` and
return an `OrderCreatedEvent`.

Domain objects in, domain events out. No repositories, no Spring.

### Step 2 — Build the `Order` from a `CreateOrderCommand`

A mapper in `order-application-service` lifting primitives into value objects:

```java
Order toOrder(CreateOrderCommand command);     // UUID → CustomerId, BigDecimal → Money
```

### Step 3 — Finish `CreateOrderUseCase`

```java
@Transactional
public CreateOrderResult execute(CreateOrderCommand command) {
    customerRepository.findCustomer(command.customerId())
            .orElseThrow(() -> new OrderDomainException("Customer not found"));
    Business business = businessRepository
            .findBusiness(command.businessId(), productIds(command))
            .orElseThrow(() -> new OrderDomainException("Business not found"));

    Order order = orderDataMapper.toOrder(command);
    OrderCreatedEvent event = orderDomainService.validateAndInitiateOrder(order, business);
    Order saved = orderRepository.saveOrder(order);
    return new CreateOrderResult(saved.getId().value());
}
```

Add `@Transactional` — `saveOrder` and event publication must succeed or fail together.

### Step 4 — Implement `OrderRepositoryAdapter.saveOrder`

Needs bidirectional `Order ⇄ OrderEntity` mapping in `OrderPersistenceMapper`, including:

- `Money ⇄ BigDecimal`, `OrderId ⇄ UUID`, `StreetAddress ⇄ OrderAddressEntity`
- `List<String> failureMessages ⇄ String` — a delimiter join, or better, a
  `@Convert`er / `@ElementCollection`
- Wiring the `OrderItemEntity.order` back-reference before saving

Note that `OrderEntity.orderId` is `@GeneratedValue(strategy = GenerationType.UUID)` while the
aggregate already assigns its own `OrderId` in `initializedOrder()`. Two id generators disagree.
Drop `@GeneratedValue` and let the domain own identity — that is the point of self-assigned ids.

### Step 5 — Handle domain exceptions at the edge

```java
@RestControllerAdvice
public class OrderGlobalExceptionHandler extends GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(OrderDomainException.class)
    public RestApiErrorResponse<?> handle(OrderDomainException e) {
        return RestApiErrorResponse.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(e.getMessage())
                .build();
    }
}
```

Also worth adding: a dedicated not-found exception → 404, and a catch-all `Exception` → 500 that
logs but does not leak a stack trace.

### Step 6 — Write tests

The architecture was built for this; there is currently nothing to show for it.

| Layer | Style | Needs |
| --- | --- | --- |
| `order-domain-core` | Plain JUnit, no Spring | `Money` arithmetic & scale; `validateOrder` rejections; every state transition and its illegal counterpart |
| `order-application-service` | JUnit + Mockito | `CreateOrderUseCase` with stubbed ports |
| `order-service-persistence` | `@DataJpaTest` | Adapters and mappers against a real schema (`spring-boot-starter-data-jpa-test` is already declared) |
| `order-service-restapi` | `@WebMvcTest` | Validation → 400 envelope; happy path → 201 |
| `order-service-main` | `@SpringBootTest` | Context loads; all beans wire |

Start with `Money` and `Order.validateOrder()` — fast, no infrastructure, and they cover the
rules that matter most.

### Step 7 — Domain events and the messaging adapter

Define a publisher port in the core:

```java
public interface OrderEventPublisher {
    void publish(OrderCreatedEvent event);
}
```

Implement it in `order-service-messaging` over Kafka. The use case calls the port; it never
imports a Kafka class.

### Step 8 — Real schema management

Replace `ddl-auto: create-drop` with `validate` plus Flyway, and add seed data for `customers`
and `businesses` — currently the tables are wiped on every restart, so there is nothing for
`findCustomer` to find.

### Step 9 — The query side

`OrderCommandController` implies `OrderQueryController`. Add
`GET /api/v1/orders/{trackingId}` with its own `GetOrderUseCase` and read-shaped DTOs.

---

## Cleanups

| Item | Action |
| --- | --- |
| Scaffold `Main.java` in `common`, `common-domain`, `common-starter`, `order-service` | Delete — IDE leftovers |
| `common-starter` | Fill in (shared auto-configuration) or drop the module |
| `order-service-restapi/pom.xml` | Remove redundant `groupId`/`version`/`name`/`url`, empty `<licenses>`/`<developers>`/`<scm>`, and the unused `org.jetbrains:annotations:13.0` |
| `ExplicitPort` | Implement it or delete it — an unused port is confusing ([ADR 0002](../decisions/0002-use-case-classes-over-input-port-interfaces.md)) |
| `Order.Builder.builder()` / `OrderItem.Builder.builder()` | Move `builder()` to the outer class, matching `Customer`/`Business`/`Product` |
| `mptc.khay.domain` split across `common-domain` and `order-domain-core` | Move the order core to `mptc.khay.order.domain` |
| Maven wrapper | `mvn wrapper:wrapper -Dmaven=3.9.16` — `maven-wrapper.properties` exists but `mvnw` does not |
| No `docker-compose.yml` | Add one for PostgreSQL on 8432 so setup is one command |
| `StreetAddress.postalcode` | Rename to `postalCode` for consistency with every other field |

---

## Suggested order of work

1. `unmappedTargetPolicy = ERROR` on both mappers, then fix everything it breaks (bugs 1–3).
2. Bugs 4–9 — small, isolated, each independently verifiable.
3. Tests for `Money` and `Order` — lock the domain down before building on it.
4. `OrderDomainService` → command→`Order` mapper → `CreateOrderUseCase` → `saveOrder`.
5. Exception handling, then an end-to-end test that really persists an order.
6. Events and messaging.
