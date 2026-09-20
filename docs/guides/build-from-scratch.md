# Build This Project From Scratch

A step-by-step walkthrough that reconstructs `Spring_study` exactly as it stands today, in the
order the pieces have to be created. Each step says **what** to make, **why** it goes there,
and **the rule** you just applied.

Follow it top to bottom and you end with the same eleven modules, the same dependency graph,
and a running `POST /api/v1/orders`.

**Contents**

1. [Set the direction before writing code](#step-0--set-the-direction-before-writing-code)
2. [Create the root aggregator](#step-1--create-the-root-aggregator)
3. [Write the root POM](#step-2--write-the-root-pom)
4. [`common-domain` — the DDD building blocks](#step-3--common-domain)
5. [`order-domain-core` — the aggregates](#step-4--order-domain-core)
6. [`order-application-service` — use cases and ports](#step-5--order-application-service)
7. [`common-restapi` — the shared error contract](#step-6--common-restapi)
8. [`order-service-restapi` — the inbound adapter](#step-7--order-service-restapi)
9. [`order-service-persistence` — the outbound adapter](#step-8--order-service-persistence)
10. [`order-service-messaging` — the placeholder](#step-9--order-service-messaging)
11. [`order-service-main` — the composition root](#step-10--order-service-main)
12. [Build, run, verify](#step-11--build-run-verify)
13. [The rules, condensed](#the-rules-condensed)

---

## Step 0 — Set the direction before writing code

The single decision that drives everything else:

> **Dependencies point inwards. The domain depends on nothing.**

You enforce it with Maven modules rather than discipline. If `order-domain-core`'s POM lists
only `common-domain`, then `import jakarta.persistence.Entity;` in an aggregate is a *compile
error*, not something a reviewer has to catch.

Draw the target graph first:

```
main ──> restapi ──────> application-service ──> domain-core ──> common-domain
 │   └─> persistence ──┘
 └─────> messaging ────┘
```

Build order is **inside-out**: the domain first, adapters last. That ordering is not just
aesthetic — you physically cannot compile an adapter before the port it implements exists.

**Naming convention used throughout:** `<service>-<ring>`. `order-domain-core`,
`order-application-service`, `order-service-restapi`, `order-service-persistence`. A module's
name tells you which ring it is in and therefore what it is allowed to import.

---

## Step 1 — Create the root aggregator

Start from [start.spring.io](https://start.spring.io) or `mvn archetype:generate`, then strip
it down: the root produces no code, only structure.

```bash
mkdir Study-spring && cd Study-spring
git init
```

`pom.xml`:

```xml
<project ...>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>mptc.khay</groupId>
    <artifactId>Spring_study</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>       <!-- aggregator: no jar -->
    <modules>
        <module>common</module>
        <module>order-service</module>
    </modules>
</project>
```

`packaging: pom` marks this as an aggregator. Inheriting `spring-boot-starter-parent` gives you
managed versions for every Spring and Jakarta artifact, so child modules omit `<version>`.

Then the two second-level aggregators, also `packaging: pom`:

```xml
<!-- common/pom.xml -->
<artifactId>common</artifactId>
<packaging>pom</packaging>
<modules>
    <module>common-starter</module>
    <module>common-domain</module>
    <module>common-restapi</module>
</modules>
```

```xml
<!-- order-service/pom.xml -->
<artifactId>order-service</artifactId>
<packaging>pom</packaging>
<modules>
    <module>order-service-domain</module>
    <module>order-service-restapi</module>
    <module>order-service-main</module>
    <module>order-service-messaging</module>
    <module>order-service-persistence</module>
</modules>
```

```xml
<!-- order-service/order-service-domain/pom.xml -->
<artifactId>order-service-domain</artifactId>
<packaging>pom</packaging>
<modules>
    <module>order-domain-core</module>
    <module>order-application-service</module>
</modules>
```

**Why a `common` tree at all?** Because this is a *microservices* study project. A future
`payment-service` needs the same `Money`, the same `BaseEntity`, the same error envelope.
`common` is what they share; `order-service` is what only orders own.

> **Rule:** aggregators carry structure, never code. Every `packaging: pom` module should have
> no `src/`.

---

## Step 2 — Write the root POM

Three blocks do all the work.

### Properties

```xml
<properties>
    <java.version>25</java.version>
    <maven-compiler-plugin.version>3.15.0</maven-compiler-plugin.version>
    <mapstruct.version>1.6.3</mapstruct.version>
    <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
</properties>
```

### `dependencyManagement` — version once, use everywhere

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>mptc.khay</groupId>
            <artifactId>common-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
        <!-- …one entry per internal module: order-domain-core, order-application-service,
             order-service-messaging, order-service-persistence, order-service-restapi,
             order-service-main, common-restapi… -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Now a child declares a sibling with no version:

```xml
<dependency>
    <groupId>mptc.khay</groupId>
    <artifactId>order-domain-core</artifactId>
</dependency>
```

`dependencyManagement` does **not** add the dependency — it only pins the version if a child
asks for it. That distinction is what lets you list every module here without every module
depending on every other.

### Lombok for everyone

```xml
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

A plain `<dependencies>` block at the root **is** inherited by every child. `optional` stops it
leaking onto downstream consumers' compile paths — correct, because Lombok is compile-time only.

### The annotation-processor chain

This is the one piece of build configuration you cannot skip, and the one that costs people an
afternoon:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>${maven-compiler-plugin.version}</version>
      <configuration>
        <release>${java.version}</release>
      </configuration>
      <executions>
        <execution>
          <id>default-compile</id>
          <phase>compile</phase>
          <goals><goal>compile</goal></goals>
          <configuration>
            <annotationProcessorPaths>
              <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
              </path>
              <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>${lombok-mapstruct-binding.version}</version>
              </path>
              <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </execution>
        <execution>
          <id>default-testCompile</id>
          <phase>test-compile</phase>
          <goals><goal>testCompile</goal></goals>
          <configuration>
            <!-- the same three paths again -->
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**Why it is fiddly.** Lombok and MapStruct are both annotation processors, and MapStruct needs
to *see* the getters, setters and builders that Lombok generates. `lombok-mapstruct-binding`
sits between them and makes that ordering work. The moment you declare
`annotationProcessorPaths` you replace the implicit classpath-scanned processors, so every
processor you want must be listed — forget Lombok there and nothing compiles.

The `default-testCompile` block repeats the list because test compilation is a separate
execution with its own processor path.

> **Rule:** the root POM owns *versions* and *build plumbing*. It owns no dependencies that a
> module might not want.

---

## Step 3 — `common-domain`

The innermost ring. Create it **first** and give it **zero dependencies**:

```xml
<artifactId>common-domain</artifactId>
<!-- no <dependencies> block at all -->
```

That empty POM is a design statement. Nothing here can import Spring, JPA or Jackson, because
none of them are on the classpath.

### Entity identity

```java
// entity/BaseEntity.java
public abstract class BaseEntity<ID> {
    private ID id;
    public ID getId()        { return id; }
    public void setId(ID id) { this.id = id; }

    @Override public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((BaseEntity<?>) o).id);
    }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
```

```java
// entity/AggregateRoot.java
public abstract class AggregateRoot<ID> extends BaseEntity<ID> { }
```

`equals` by **id only** is the DDD definition of an entity: same identity, same thing, whatever
the other fields say. `AggregateRoot` adds no behaviour — it adds *meaning*. It marks the only
types a repository may load or save.

### Value objects as records

```java
public record OrderId(UUID value) { }
public record CustomerId(UUID value) { }
public record BusinessId(UUID value) { }
public record ProductId(UUID value) { }
public record TrackingId(UUID value) { }
public record OrderItemId(Integer value) { }
public record StreetAddress(UUID id, String postalcode, String street, String city) { }

public enum OrderStatus { PENDING, PAID, APPROVED, CANCELLING, CANCELLED }
```

Records give you immutability, structural `equals`/`hashCode` and a compact constructor for
free — exactly the semantics of a value object.

Wrapping `UUID` in a named type is not ceremony. `void ship(OrderId o, CustomerId c)` cannot be
called with the arguments swapped; `void ship(UUID o, UUID c)` can, and the compiler will never
tell you.

### The one value object with behaviour

```java
public record Money(BigDecimal amount) {
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public boolean isGreaterThanZero()    { return amount.compareTo(BigDecimal.ZERO) > 0; }
    public boolean isGreaterThan(Money m) { return amount.compareTo(m.amount) > 0; }
    public Money add(Money m)             { return new Money(setScale(amount.add(m.amount))); }
    public Money subtract(Money m)        { return new Money(setScale(amount.subtract(m.amount))); }
    public Money multiply(int multiplier) { return new Money(setScale(amount.multiply(BigDecimal.valueOf(multiplier)))); }

    private BigDecimal setScale(BigDecimal in) { return in.setScale(2, RoundingMode.HALF_EVEN); }
}
```

Rounding policy is now defined in exactly one place instead of scattered across every service
that touches a price. This is the standard argument for value objects with behaviour.

### Events and exceptions

```java
public interface DomainEvent<T> { }

public class DomainException extends RuntimeException {
    public DomainException(String message)                  { super(message); }
    public DomainException(String message, Throwable cause) { super(message, cause); }
}
```

Unchecked, because a violated business rule is a programming-level "this must not happen",
not a condition every caller should be forced to declare.

> **Rule:** if a type would be identical in a payment service or a shipping service, it belongs
> in `common-domain`. If it mentions orders, it does not.

---

## Step 4 — `order-domain-core`

```xml
<artifactId>order-domain-core</artifactId>
<dependencies>
    <dependency>
        <groupId>mptc.khay</groupId>
        <artifactId>common-domain</artifactId>
    </dependency>
</dependencies>
```

One dependency. Still no Spring, still no JPA.

### 4a. The supporting entities

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
    // getters + static final class Builder { … }
}
```

`Customer extends AggregateRoot<CustomerId>` and `Business extends AggregateRoot<BusinessId>`
follow the same shape: `final` fields, private constructor, static builder.

`Business` holds `List<Product> products` and `boolean active` — the catalogue an order gets
validated against.

> **Consistency note.** `Product`, `Customer` and `Business` expose `builder()` on the *outer*
> class (`Customer.builder()`), while `Order` and `OrderItem` put it inside the `Builder` class
> (`Order.Builder.builder()`). Pick one and stick to it; the outer-class form is conventional.

### 4b. The `Order` aggregate

Fields first, and be deliberate about `final`:

```java
public class Order extends AggregateRoot<OrderId> {
    private final CustomerId customerId;
    private final BusinessId businessId;
    private final StreetAddress deliveryAddress;
    private final Money price;
    private final List<OrderItem> items;

    private TrackingId trackingId;          // assigned at initialisation
    private OrderStatus orderStatus;        // moves through the lifecycle
    private List<String> failureMessages;   // accumulates on cancellation
}
```

Everything that defines *what this order is* is `final`. Only what legitimately changes over
its life is mutable — and each mutable field is only ever touched by a method that checks the
current state first.

Note `customerId` and `businessId`: **other aggregates are referenced by id, never by object
reference**. That keeps the transactional boundary at one aggregate and prevents an accidental
`order.getCustomer().getOrders()` from loading the world.

**Invariants:**

```java
public void validateOrder() {
    validateInitialOrder();
    validateTotalPrice();
    validateItemsPrice();
}

private void validateInitialOrder() {
    if (orderStatus != null || super.getId() != null) {
        throw new OrderDomainException("Order is not in correct status for initialization");
    }
}

private void validateTotalPrice() {
    if (price == null || !price.isGreaterThanZero()) {
        throw new OrderDomainException("Total price must be greater than zero");
    }
}

private void validateItemsPrice() {
    Money itemsTotal = items.stream()
            .map(item -> { validateItemPrice(item); return item.getSubTotal(); })
            .reduce(Money.ZERO, Money::add);
    if (!price.equals(itemsTotal)) {
        throw new OrderDomainException("Total price " + price.amount()
                + " is not equal to order items total " + itemsTotal.amount());
    }
}
```

The aggregate refuses to exist in an invalid state. The client sends its own arithmetic, and
the domain checks it against the catalogue rather than trusting it.

**Self-assigned identity:**

```java
public void initializedOrder() {
    setId(new OrderId(UUID.randomUUID()));
    trackingId = new TrackingId(UUID.randomUUID());
    orderStatus = OrderStatus.PENDING;
    initializeOrderItems();
}

private void initializeOrderItems() {
    int itemCount = 1;
    for (OrderItem item : items) {
        item.initializeOrderItem(super.getId(), new OrderItemId(itemCount++));
    }
}
```

The domain generates its own UUIDs instead of waiting for a database sequence. This is what
makes the aggregate testable with no database at all, and it means the id exists before the
save, so a domain event can carry it.

**The state machine — one guarded method per transition:**

```java
public void pay() {
    if (orderStatus != OrderStatus.PENDING)
        throw new OrderDomainException("Order is not in correct state for pay operation");
    orderStatus = OrderStatus.PAID;
}

public void approve()   { /* requires PAID      → APPROVED  */ }
public void initCancel() { /* requires PAID      → CANCELLING */ }
public void cancel()    { /* requires CANCELLING or PENDING → CANCELLED */ }
```

There is no `setOrderStatus`. The only way to change status is through a method that names a
business operation and enforces its precondition. That is the entire point of an aggregate.

`CANCELLING` exists because cancelling a *paid* order needs the payment reversed first — the
order side of a saga. A `PENDING` order has nothing to compensate, so it can go straight to
`CANCELLED`.

### 4c. `OrderItem` — inside the aggregate

```java
public class OrderItem extends BaseEntity<OrderItemId> {
    private OrderId orderId;                 // set by the parent at initialisation
    private final Product product;
    private final Integer quantity;
    private final Money price;
    private final Money subTotal;

    public void initializeOrderItem(OrderId orderId, OrderItemId orderItemId) {
        this.orderId = orderId;
        super.setId(orderItemId);
    }

    public boolean isPriceValid() {
        return price.isGreaterThanZero()
            && price.equals(product.getPrice())
            && price.multiply(quantity).equals(subTotal);
    }
}
```

`initializeOrderItem` is package-visible in spirit — only `Order` should call it. An
`OrderItem` has no meaning outside its order, which is why there is no `OrderItemRepository`.

### 4d. Events and the domain service

```java
public abstract class OrderEvent implements DomainEvent<Order> {
    private final Order order;
    private final ZonedDateTime createdAt;
}
public class OrderCreatedEvent   extends OrderEvent { /* super(order, createdAt) */ }
public class OrderPaidEvent      extends OrderEvent { /* … */ }
public class OrderCancelledEvent extends OrderEvent { /* … */ }
```

```java
public interface OrderDomainService { }          // still empty in this codebase
public class OrderDomainServiceImp implements OrderDomainService { }
```

A domain service is for rules that need **more than one aggregate**. The target signatures:

```java
OrderCreatedEvent   validateAndInitiateOrder(Order order, Business business);
OrderPaidEvent      payOrder(Order order);
OrderCancelledEvent cancelOrderPayment(Order order, List<String> failureMessages);
```

Domain objects in, domain events out. No repositories — *loading* the `Business` is the use
case's job; *judging* the order against it is the domain service's.

> **Rule:** a business rule belongs on the aggregate that owns the data it constrains. Only when
> it genuinely spans aggregates does it move to a domain service. It never starts in a use case.

---

## Step 5 — `order-application-service`

```xml
<artifactId>order-application-service</artifactId>
<dependencies>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-domain-core</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
</dependencies>
```

`spring-context` only — enough for `@Component`, not enough for HTTP or JPA. That is a
deliberate line: the application ring may be *managed* by Spring, but it may not know about any
particular transport or database.

### 5a. Commands and results

```java
public record CreateOrderCommand(
        UUID customerId,
        UUID businessId,
        CommandOrderAddress deliveryAddress,
        BigDecimal price,
        List<CommandOrderItem> items) { }

public record CommandOrderItem(UUID productId, Integer quantity, BigDecimal subtotal, BigDecimal price) { }
public record CommandOrderAddress(String street, String postalCode, String city) { }
public record CreateOrderResult(UUID orderId) { }
```

Note the naming: **`Command` / `Result`, not `Request` / `Response`**. `Request` belongs to
HTTP; these DTOs must stay meaningful when the caller is a Kafka consumer or a scheduled job.
Renaming them from `CreateOrderRequest`/`CreateOrderResponse` is one of the real refactors in
this project's history.

They carry primitives (`UUID`, `BigDecimal`), not value objects. Converting `BigDecimal` →
`Money` is the use case's job, on the way in.

### 5b. Output ports

```java
public interface OrderRepository {
    Order saveOrder(Order order);
}
public interface CustomerRepository {
    Optional<Customer> findCustomer(UUID customerID);
}
public interface BusinessRepository {
    Optional<Business> findBusiness(UUID businessId, List<UUID> productIds);
}
```

This is the inversion that makes the whole architecture work. The interface is defined **here,
in the core**, and implemented out in the persistence adapter. The core says what it needs; the
infrastructure conforms.

Three properties to copy:

- Return **domain types** (`Optional<Customer>`), never `CustomerEntity`.
- Shape the method around the **use case**, not the table. `findBusiness(businessId, productIds)`
  exists because creating an order needs exactly that.
- Use `Optional` — absence is part of the contract, not a surprise `null`.

### 5c. Input ports

```java
public interface ExplicitPort {
    void execute(CreateOrderCommand createOrderCommand);
}
```

This is the "declare an interface for the driving side too" style. It is written here as an
example but not used; the controller calls the concrete `CreateOrderUseCase` class instead.
See [ADR 0002](../decisions/0002-use-case-classes-over-input-port-interfaces.md) for the
trade-off.

### 5d. The use case

Current state — a deliberate walking skeleton:

```java
@Component
@Slf4j
public class CreateOrderUseCase {
    public CreateOrderResult execute(CreateOrderCommand createOrderCommand) {
        log.info("executing CreateOrderUseCase: {}", createOrderCommand);
        return new CreateOrderResult(UUID.randomUUID());
    }
}
```

Wiring first, logic second: this proves controller → mapper → use case → mapper → response
works end to end before any business logic exists. The target:

```java
@Component
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final OrderDomainService orderDomainService;
    private final OrderDataMapper orderDataMapper;

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
        // eventPublisher.publish(event);
        return new CreateOrderResult(saved.getId().value());
    }
}
```

Read what it does: **load, delegate, save, publish, return**. Orchestration only. There is no
`if` about business correctness anywhere in it — those live in `validateAndInitiateOrder` and
on the aggregate.

> **Rule:** a use case is a transaction script. The moment it starts comparing prices or
> checking statuses, that logic has escaped from the domain and should be pushed back.

---

## Step 6 — `common-restapi`

Before the order-specific web layer, define how *every* service in this codebase reports errors.

```xml
<artifactId>common-restapi</artifactId>
<dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId></dependency>
</dependencies>
```

> Spring Boot 4 renamed `spring-boot-starter-web` to `spring-boot-starter-webmvc`. The
> `*-test` starters were modularised the same way (`spring-boot-starter-webmvc-test`,
> `spring-boot-starter-data-jpa-test`).

```java
@Builder
public record RestApiErrorResponse<T>(String code, String message, T detail) { }

public record FieldErrorResponse(String field, String code, String reason) { }
```

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestApiErrorResponse<?> handleException(MethodArgumentNotValidException e) {
        return RestApiErrorResponse.builder()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Data validation failed")
                .detail(extractFieldErrors(e.getFieldErrors()))
                .build();
    }

    private List<FieldErrorResponse> extractFieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(fe -> new FieldErrorResponse(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .toList();
    }
}
```

### The component-scan trap

`GlobalExceptionHandler` is in package `mptc.khay.restapi.exception`. The Boot application will
sit in `mptc.khay.order`, and `@SpringBootApplication` scans **its own package downwards**.
`mptc.khay.restapi` is *not* under `mptc.khay.order`, so this bean is never found.

The fix — applied in the next step — is a subclass inside a scanned package:

```java
@RestControllerAdvice
public class OrderGlobalExceptionHandler extends GlobalExceptionHandler { }
```

Alternatives are `@ComponentScan(basePackages = {"mptc.khay.order", "mptc.khay.restapi"})` or
shipping `common-restapi` as an auto-configuration. The subclass wins here because it doubles
as the natural home for order-specific handlers.

> **Rule:** every shared Spring bean needs a deliberate answer to "how does component scan
> reach this?". Package layout is wiring.

---

## Step 7 — `order-service-restapi`

The inbound adapter.

```xml
<artifactId>order-service-restapi</artifactId>
<dependencies>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-application-service</artifactId></dependency>
    <dependency><groupId>mptc.khay</groupId><artifactId>common-restapi</artifactId></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework</groupId><artifactId>spring-web</artifactId></dependency>
</dependencies>
```

### 7a. Request/response DTOs with validation at the edge

```java
@Builder
public record OrderCreateRequest(
        @NotNull UUID customerId,
        @NotNull UUID businessId,
        @NotNull @Valid OrderAddressRequest orderAddress,
        @NotNull @Valid List<OrderItemRequest> items,
        @NotNull BigDecimal price) { }

@Builder
public record OrderAddressRequest(
        @NotNull @Size(max = 20) String street,
        @NotNull @Size(max = 10) String postalCode,
        @NotNull @Size(max = 30) String city) { }

@Builder
public record OrderItemRequest(
        @NotNull UUID productId,
        @NotNull Integer quantity,
        @NotNull BigDecimal price,
        @NotNull BigDecimal subTotal) { }

@Builder
public record OrderCreateResponse(UUID orderId) { }
```

`@Valid` on `orderAddress` and on `items` is what makes validation *cascade* into nested objects
and list elements. Without it the outer `@NotNull` passes and the inner constraints are silently
skipped.

**Structural** validation lives here, at the edge — cheap, and it runs before anything is
loaded. **Business** validation (is this price right?) stays in the domain. Do not mix them.

### 7b. The mapper

```java
@Mapper(componentModel = "spring")
public interface OrderWebMapper {
    CreateOrderCommand orderCreateRequestToCreateOrderCommand(OrderCreateRequest orderCreateRequest);
    OrderCreateResponse createOrderResultToOrderCreateResponse(CreateOrderResult createOrderResult);
}
```

You write the interface; MapStruct writes `OrderWebMapperImpl` into
`target/generated-sources/annotations/` and annotates it `@Component` (that is what
`componentModel = "spring"` buys). Nested lists are handled automatically.

> ⚠️ **Read the generated `*Impl`.** MapStruct matches fields **by name, case-sensitively**, and
> its default `unmappedTargetPolicy` is `WARN` — a mismatch compiles clean and produces `null`
> at runtime. This project has two live examples: `orderAddress` → `deliveryAddress`, and
> `subTotal` → `subtotal`, both currently `null`. Fix with explicit mappings:
> ```java
> @Mapping(source = "orderAddress", target = "deliveryAddress")
> CreateOrderCommand orderCreateRequestToCreateOrderCommand(OrderCreateRequest r);
> ```
> and make it impossible to repeat:
> ```java
> @Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
> ```

### 7c. The controller

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper orderWebMapper;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public OrderCreateResponse createOrder(@Valid @RequestBody OrderCreateRequest orderCreateRequest) {
        CreateOrderCommand command = orderWebMapper.orderCreateRequestToCreateOrderCommand(orderCreateRequest);
        CreateOrderResult result = createOrderUseCase.execute(command);
        return orderWebMapper.createOrderResultToOrderCreateResponse(result);
    }
}
```

Three lines, all translation or delegation. `final` fields plus `@RequiredArgsConstructor` gives
constructor injection without writing a constructor — the preferred form, because the class
cannot be constructed in an invalid state and is trivially testable with `new`.

The name `OrderCommandController` reserves room for an `OrderQueryController` (CQRS).

### 7d. The scannable exception handler

```java
@RestControllerAdvice
public class OrderGlobalExceptionHandler extends GlobalExceptionHandler {
    // add @ExceptionHandler(OrderDomainException.class) here
}
```

> **Rule:** controllers contain no business logic, no `if`, no repository access. If you want
> one of those, you are in the wrong ring.

---

## Step 8 — `order-service-persistence`

The outbound adapter: implement the ports written in step 5b.

```xml
<artifactId>order-service-persistence</artifactId>
<dependencies>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-application-service</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Note the direction: persistence depends on the core. The core has no idea this module exists.

### 8a. JPA entities — a *separate* model

```java
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "orders")
public class OrderEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID orderId;
    private UUID customerId;
    private UUID businessId;
    private BigDecimal price;

    @OneToMany(mappedBy = "order")
    private List<OrderItemEntity> items;

    @OneToOne
    private OrderAddressEntity orderAddress;

    private OrderStatus orderStatus;
    private UUID trackingId;
    private String failureMessages;
}
```

Deliberately **not** the `Order` aggregate. It is flat, mutable, has a no-arg constructor and
setters (all of which JPA requires and DDD forbids), and stores `failureMessages` as a single
`String` because that is convenient for a column. The aggregate keeps `Money`, `OrderId`,
`List<String>` and its invariants. Two models, one mapper between them — see
[ADR 0003](../decisions/0003-jpa-entities-separate-from-aggregates.md).

`OrderItemEntity` uses `GenerationType.IDENTITY` with `@ManyToOne private OrderEntity order`;
`OrderAddressEntity` uses `@OneToOne(mappedBy = "orderAddress")`.

**Composite key** for the denormalised business/product table:

```java
@IdClass(BusinessIdEntity.class)
@Entity @Table(name = "businesses")
public class BusinessEntity {
    @Id private UUID businessId;
    @Id private UUID productId;
    private Boolean businessActive;
    private String productName;
    private BigDecimal productPrice;
}

@Getter @Setter @NoArgsConstructor @EqualsAndHashCode
public class BusinessIdEntity implements Serializable {
    private UUID businessId;
    private UUID productId;
}
```

JPA requires the id class to be `Serializable` with `equals`/`hashCode` — Lombok's
`@EqualsAndHashCode` covers the last two.

### 8b. Spring Data repositories

```java
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> { }

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> { }

public interface BusinessJpaRepository extends JpaRepository<BusinessEntity, BusinessIdEntity> {
    List<BusinessEntity> findByBusinessIdAndProductIdIn(UUID businessId, List<UUID> productIds);
}
```

`findByBusinessIdAndProductIdIn` is a *derived query*: Spring Data parses the method name and
generates the SQL. No implementation, no `@Query`.

These are internal to the adapter. They are **not** the ports — nothing outside this module
imports them.

### 8c. The persistence mapper

```java
@Mapper(componentModel = "spring")
public interface OrderPersistenceMapper {

    @Mapping(source = "id", target = "id.value")
    Customer customerEntityToCustomer(CustomerEntity customerEntity);

    @Mapping(source = "productId",    target = "id.value")
    @Mapping(source = "productName",  target = "name")
    @Mapping(source = "productPrice", target = "price.amount")
    Product businessEntityToProduct(BusinessEntity businessEntity);

    // The businesses table has one row per product, so every row carries the same business id and active flag
    default Business businessEntitiesToBusiness(List<BusinessEntity> businessEntities) {
        BusinessEntity first = businessEntities.getFirst();
        return Business.builder()
                .id(new BusinessId(first.getBusinessId()))
                .active(Boolean.TRUE.equals(first.getBusinessActive()))
                .products(businessEntities.stream().map(this::businessEntityToProduct).toList())
                .build();
    }
}
```

`target = "id.value"` tells MapStruct to construct the `CustomerId` record around the raw UUID —
this is how a flat persistence row is lifted back into typed value objects.

When the shapes genuinely differ — many rows folding into one aggregate — drop to a `default`
method and write it by hand. That reshaping is exactly the work the adapter exists to absorb.

### 8d. The adapters

```java
@Repository
@RequiredArgsConstructor
public class BusinessRepositoryAdapter implements BusinessRepository {

    private final BusinessJpaRepository businessJpaRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public Optional<Business> findBusiness(UUID businessId, List<UUID> productIds) {
        List<BusinessEntity> entities =
                businessJpaRepository.findByBusinessIdAndProductIdIn(businessId, productIds);
        return entities.isEmpty()
                ? Optional.empty()
                : Optional.of(orderPersistenceMapper.businessEntitiesToBusiness(entities));
    }
}
```

```java
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public Optional<Customer> findCustomer(UUID customerID) {
        return customerJpaRepository.findById(customerID)
                .map(orderPersistenceMapper::customerEntityToCustomer);
    }
}
```

`OrderRepositoryAdapter` is the one still outstanding:

```java
@Override
public Order saveOrder(Order order) {
    return null;   // TODO: needs Order ⇄ OrderEntity mapping both ways
}
```

> **Rule:** `JpaRepository` never leaves this module. The adapter is the wrapper that converts
> a framework interface into a domain-owned port.

---

## Step 9 — `order-service-messaging`

```xml
<artifactId>order-service-messaging</artifactId>
<dependencies>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-application-service</artifactId></dependency>
</dependencies>
```

POM only, no code. Reserved for the Kafka adapter that will implement a future event-publisher
port and publish `OrderCreatedEvent`.

Creating the empty module now is not waste: it fixes the dependency direction before anyone is
tempted to publish an event straight from the use case.

---

## Step 10 — `order-service-main`

The composition root — the only module that knows the whole system.

```xml
<artifactId>order-service-main</artifactId>
<dependencies>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-application-service</artifactId></dependency>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-service-messaging</artifactId></dependency>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-service-persistence</artifactId></dependency>
    <dependency><groupId>mptc.khay</groupId><artifactId>order-service-restapi</artifactId></dependency>

    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>

    <dependency>
        <groupId>org.postgresql</groupId><artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

The PostgreSQL driver is `runtime` scope **and it is only declared here**. No other module can
accidentally compile against a PostgreSQL class. Changing database means editing this one POM.

```java
package mptc.khay.order;

@EntityScan(basePackages = "mptc.khay.order.persistence")
@EnableJpaRepositories(basePackages = "mptc.khay.order.persistence")
@SpringBootApplication
public class OrderServiceApplication {
    static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

**Package placement is the wiring.** `mptc.khay.order` sits above `mptc.khay.order.restapi`,
`mptc.khay.order.persistence` and `mptc.khay.order.domain`, so one component scan picks up the
controller, the use case, the repository adapters and the generated mappers across four jars.

Get this wrong and nothing is found. It is the single most common failure when splitting a
Spring app into modules.

`@EntityScan` / `@EnableJpaRepositories` are strictly redundant given that placement, but they
document the intent and keep things working if the persistence package ever moves out from
under `mptc.khay.order`.

> In Spring Boot 4, `@EntityScan` moved to `org.springframework.boot.persistence.autoconfigure`.

`application.yaml`:

```yaml
server:
  port: 8550

spring:
  application:
    name: order-service
  datasource:
    driver-class-name: org.postgresql.Driver
    username: spring_usr
    password: root
    url: jdbc:postgresql://localhost:8432/db_order
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

`create-drop` is a study-project choice: tables are built from the entities on start and dropped
on stop. For anything real, use `validate` plus Flyway.

> **Keep exactly one `application.yaml`, here.** An adapter module that ships its own puts a
> second one on the classpath and only one wins — a genuinely confusing bug.
> `order-service-restapi` currently has one; it should be deleted.

> **Rule:** the composition root is the only module allowed to depend on everything. Nothing
> depends on it. That is what makes an adapter swappable by editing one POM.

---

## Step 11 — Build, run, verify

```bash
mvn clean install                                          # all 11 modules, in order
mvn -pl order-service/order-service-main spring-boot:run   # start on :8550
```

Verify the build actually did what you think:

```bash
find . -path '*generated-sources*' -name '*MapperImpl.java'
```

You should see `OrderWebMapperImpl` and `OrderPersistenceMapperImpl`. **Open them.** The
generated code is the only reliable statement of what your mappers really do.

Then exercise both paths:

```bash
# happy path → 201 with an orderId
curl -s -X POST localhost:8550/api/v1/orders -H 'Content-Type: application/json' -d '{
  "customerId":"7b0e0f5c-1c3f-4f2c-9a5b-6f4f8d2e1a10",
  "businessId":"3f2a9d61-8b47-4f2a-9c10-0d5a7e3b9c22",
  "orderAddress":{"street":"1 Main St","postalCode":"10110","city":"Bangkok"},
  "items":[{"productId":"c3d4e5f6-a7b8-4901-8234-56789abcdef0","quantity":2,"price":25.00,"subTotal":50.00}],
  "price":50.00}'

# validation path → 400 with the field-error envelope
curl -s -X POST localhost:8550/api/v1/orders -H 'Content-Type: application/json' -d '{}'
```

Full setup detail in [getting-started.md](getting-started.md).

---

## The rules, condensed

Keep these on hand while extending the project:

1. **Dependencies point inwards.** Enforce with POMs, not code review.
2. **`order-domain-core` imports nothing but `common-domain`.** No Spring, no JPA, no Jackson.
3. **Business rules live on the aggregate** that owns the data. Cross-aggregate rules go to a
   domain service. Never in a controller or a use case.
4. **A use case orchestrates: load, delegate, save, publish, return.** No business `if`s.
5. **Ports are owned by the core** and phrased in domain types.
6. **Each ring has its own DTOs.** Request/Response at the edge, Command/Result in the
   application ring, aggregates in the domain, `@Entity` in persistence. Mappers at the seams.
7. **Reference other aggregates by id**, never by object reference.
8. **Aggregates generate their own identity** so the domain is testable without a database.
9. **No setters on aggregates.** Every mutation is a named operation that guards its
   precondition.
10. **Validation splits by kind:** structural at the edge (`@Valid`), business in the domain.
11. **The composition root owns wiring and config** — one `application.yaml`, one place the
    driver is declared.
12. **Component scan follows packages.** Put the Boot class in the parent package of every
    scanned module, or say `@ComponentScan` explicitly.
13. **Always read the generated MapStruct `*Impl`**, and set
    `unmappedTargetPolicy = ReportingPolicy.ERROR` so silent `null`s become build failures.

## What is not done yet

This walkthrough reproduces the project as it stands, stubs included. For the list of what is
still missing — the empty domain service, the stubbed use case and `saveOrder`, the two mapper
bugs, the absent tests — see
[current-state-and-next-steps.md](current-state-and-next-steps.md).
