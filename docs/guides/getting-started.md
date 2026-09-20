# Getting Started

How to build and run `order-service` locally.

## Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | **25** (the build sets `release=25`) | `java -version` |
| Maven | 3.9.x | `mvn -v` |
| PostgreSQL | 13+ | `psql --version` |

> **No Maven wrapper script.** `.mvn/wrapper/maven-wrapper.properties` exists (pinning Maven
> 3.9.16) but `mvnw` / `mvnw.cmd` are not in the repo, so use a locally installed `mvn`.
> To restore the wrapper: `mvn wrapper:wrapper -Dmaven=3.9.16`.

Java 25 is not optional here — the code uses Java 25 language features. `OrderServiceApplication`
declares a package-private `static void main(String[] args)`, and the leftover scaffold
`Main.java` files use `IO.println`, both of which come from the finalised compact-source-file
and instance-main-method work in Java 25.

## 1. Start PostgreSQL

The datasource in `order-service-main/src/main/resources/application.yaml` expects:

| Setting | Value |
| --- | --- |
| Host / port | `localhost:8432` (note: **not** the default 5432) |
| Database | `db_order` |
| Username | `spring_usr` |
| Password | `root` |

The repository has no `docker-compose.yml`. The quickest way to match that config:

```bash
docker run -d --name study-postgres \
  -e POSTGRES_DB=db_order \
  -e POSTGRES_USER=spring_usr \
  -e POSTGRES_PASSWORD=root \
  -p 8432:5432 \
  postgres:16
```

Verify:

```bash
docker exec -it study-postgres psql -U spring_usr -d db_order -c '\dt'
```

### Schema

`spring.jpa.hibernate.ddl-auto: create-drop`, so **Hibernate creates the tables at startup and
drops them at shutdown**. You do not need a migration tool to get running, and you lose all
data on every restart.

Tables generated from the `@Entity` classes: `orders`, `order_items`, `order_addresses`,
`customers`, `businesses`.

Because `create-drop` wipes everything, `customers` and `businesses` are empty on each boot.
Once `CreateOrderUseCase` actually looks those rows up, you will need seed data — at which
point switch to `ddl-auto: validate` plus Flyway or Liquibase.

## 2. Build

From the repository root:

```bash
mvn clean install
```

This builds all eleven modules in dependency order. `install` (not `package`) is what you want:
the modules depend on each other through the local repository.

Two things happen during compilation that are worth knowing about:

- **Lombok** generates constructors and accessors (`@RequiredArgsConstructor`, `@Getter`, `@Builder`).
- **MapStruct** generates the mapper implementations into
  `target/generated-sources/annotations/` — e.g.
  `order-service-restapi/target/generated-sources/annotations/mptc/khay/order/restapi/mapper/OrderWebMapperImpl.java`.

Both are configured in the root `pom.xml` under `maven-compiler-plugin` →
`annotationProcessorPaths`, with `lombok-mapstruct-binding` between them so the two processors
cooperate.

> If a mapper appears not to map a field, **read the generated `*Impl`**. It is the ground
> truth, and MapStruct only *warns* about unmapped fields by default. See
> [ports-and-adapters.md](../architecture/ports-and-adapters.md#mapping-between-the-rings).

Build a single module and its dependencies:

```bash
mvn -pl order-service/order-service-restapi -am clean install
```

## 3. Run

```bash
mvn -pl order-service/order-service-main spring-boot:run
```

or from the built jar:

```bash
java -jar order-service/order-service-main/target/order-service-main-0.0.1-SNAPSHOT.jar
```

Only `order-service-main` is runnable — it is the composition root that depends on all the
adapters. The service listens on **8550**.

Expected startup signs:

- Hibernate DDL in the log (`show-sql: true`, `format_sql: true`).
- `Tomcat started on port 8550`.

## 4. Call it

```bash
curl -i -X POST http://localhost:8550/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "7b0e0f5c-1c3f-4f2c-9a5b-6f4f8d2e1a10",
    "businessId": "3f2a9d61-8b47-4f2a-9c10-0d5a7e3b9c22",
    "orderAddress": { "street": "1 Main St", "postalCode": "10110", "city": "Bangkok" },
    "items": [
      { "productId": "c3d4e5f6-a7b8-4901-8234-56789abcdef0",
        "quantity": 2, "price": 25.00, "subTotal": 50.00 }
    ],
    "price": 50.00
  }'
```

→ `201 Created` with a random `orderId`. Check the log for
`executing CreateOrderUseCase: CreateOrderCommand[...]`, and note that `deliveryAddress=null`
in that line — one of the [known mapper bugs](current-state-and-next-steps.md).

Now break it on purpose:

```bash
curl -i -X POST http://localhost:8550/api/v1/orders \
  -H 'Content-Type: application/json' -d '{}'
```

→ `400 Bad Request` with the `RestApiErrorResponse` envelope listing every `@NotNull`
violation. That is `GlobalExceptionHandler` doing its job, reached through the
`OrderGlobalExceptionHandler` subclass. Full contract in [../api/orders-api.md](../api/orders-api.md).

## Tests

There are none yet. `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` and
`spring-boot-starter-data-jpa-test` are already declared in the relevant modules, so
`src/test/java` is ready to use.

The payoff of this architecture is where you can start: `order-domain-core` has no Spring and
no database, so `Order`, `OrderItem` and `Money` are testable with plain JUnit and plain
constructors — no `@SpringBootTest`, no mocks, milliseconds per test.

```bash
mvn test                                        # everything
mvn -pl order-service/order-service-domain/order-domain-core test   # one module
```

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `release version 25 not supported` | JDK older than 25 | Install JDK 25, set `JAVA_HOME` |
| `Connection refused` on 8432 | PostgreSQL not running, or on 5432 | Start the container above, or change the `url` in `application.yaml` |
| `Could not resolve dependencies for mptc.khay:...` | A sibling module is not installed | Run `mvn clean install` from the **root**, not from a submodule |
| Mapper bean not found | Build ran without annotation processing | `mvn clean install`; confirm `target/generated-sources/annotations/` has the `*MapperImpl` |
| `spring.application.name` is `order-service-restapi` | Two `application.yaml` files on the classpath — `order-service-restapi` ships one too | Delete `order-service-restapi/src/main/resources/application.yaml`; config belongs in the composition root |
| Stale mapper behaviour after a rename | Old generated sources reused | `mvn clean` before rebuilding |

## Where next

- [build-from-scratch.md](build-from-scratch.md) — rebuild the whole thing from an empty folder.
- [../architecture/overview.md](../architecture/overview.md) — why it is shaped this way.
- [current-state-and-next-steps.md](current-state-and-next-steps.md) — what to implement next.
