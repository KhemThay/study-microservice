# Orders API

Base URL: `http://localhost:8550` (`server.port` in `order-service-main/src/main/resources/application.yaml`).

| Method | Path | Purpose | Status |
| --- | --- | --- | --- |
| `POST` | `/api/v1/orders` | Create an order | Implemented as a stub — see [caveats](#current-caveats) |

Defined in `order-service-restapi/.../controller/OrderCommandController.java`.

---

## `POST /api/v1/orders`

Creates an order. Returns **201 Created**.

### Request body — `OrderCreateRequest`

| Field | Type | Constraints | Notes |
| --- | --- | --- | --- |
| `customerId` | UUID | `@NotNull` | Must identify an existing customer |
| `businessId` | UUID | `@NotNull` | The business the order is placed with |
| `orderAddress` | object | `@NotNull`, `@Valid` | Delivery address, see below |
| `items` | array | `@NotNull`, `@Valid` | Order lines, see below. Not currently constrained to be non-empty |
| `price` | decimal | `@NotNull` | Order total. Must equal the sum of item `subTotal`s |

#### `orderAddress` — `OrderAddressRequest`

| Field | Type | Constraints |
| --- | --- | --- |
| `street` | string | `@NotNull`, `@Size(max = 20)` |
| `postalCode` | string | `@NotNull`, `@Size(max = 10)` |
| `city` | string | `@NotNull`, `@Size(max = 30)` |

#### `items[]` — `OrderItemRequest`

| Field | Type | Constraints |
| --- | --- | --- |
| `productId` | UUID | `@NotNull` |
| `quantity` | integer | `@NotNull` |
| `price` | decimal | `@NotNull` — unit price, must match the catalogue price |
| `subTotal` | decimal | `@NotNull` — must equal `price × quantity` |

The redundancy between `price`, `subTotal` and the order-level `price` is intentional: the
client states its arithmetic and `Order.validateOrder()` refuses the order if the numbers do
not agree with the product catalogue. See
[domain-model.md](../architecture/domain-model.md#invariants--validateorder).

### Example request

```bash
curl -i -X POST http://localhost:8550/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "7b0e0f5c-1c3f-4f2c-9a5b-6f4f8d2e1a10",
    "businessId": "3f2a9d61-8b47-4f2a-9c10-0d5a7e3b9c22",
    "orderAddress": {
      "street": "1 Main St",
      "postalCode": "10110",
      "city": "Bangkok"
    },
    "items": [
      {
        "productId": "c3d4e5f6-a7b8-4901-8234-56789abcdef0",
        "quantity": 2,
        "price": 25.00,
        "subTotal": 50.00
      },
      {
        "productId": "d4e5f6a7-b8c9-4012-9345-6789abcdef01",
        "quantity": 1,
        "price": 15.50,
        "subTotal": 15.50
      }
    ],
    "price": 65.50
  }'
```

### Success response — `201 Created`

```json
{
  "orderId": "1f4c8f5b-2a6d-4c3e-9b7a-8d0e2f3a4b5c"
}
```

`OrderCreateResponse` currently carries only `orderId`. A fuller implementation would also
return `trackingId` and `orderStatus`, both of which the `Order` aggregate already produces in
`initializedOrder()`.

---

## Error responses

Every error uses the shared envelope `RestApiErrorResponse<T>` from `common-restapi`:

```json
{
  "code": "string",
  "message": "string",
  "detail": null
}
```

### `400 Bad Request` — validation failure

Produced by `GlobalExceptionHandler` when `@Valid` rejects the body. `detail` is an array of
`FieldErrorResponse`.

```json
{
  "code": "Bad Request",
  "message": "Data validation failed",
  "detail": [
    {
      "field": "customerId",
      "code": "NotNull",
      "reason": "must not be null"
    },
    {
      "field": "orderAddress.street",
      "code": "Size",
      "reason": "size must be between 0 and 20"
    }
  ]
}
```

| Field of `detail[]` | Source |
| --- | --- |
| `field` | `FieldError.getField()` — dotted path, including nested and indexed paths like `items[0].price` |
| `code` | `FieldError.getCode()` — the constraint name, e.g. `NotNull`, `Size` |
| `reason` | `FieldError.getDefaultMessage()` — the human-readable message |

### Not yet handled

`OrderGlobalExceptionHandler` is an empty subclass with a `// TODO: write the exception`
marker, so anything other than a bean-validation failure falls through to Spring's default
error handling:

| Situation | Today | Intended |
| --- | --- | --- |
| `OrderDomainException` (invalid price, illegal state transition) | 500 with Spring's default body | 400 with the envelope |
| Customer or business not found | n/a — not looked up yet | 404 |
| Malformed JSON (`HttpMessageNotReadableException`) | 400 with Spring's default body | 400 with the envelope |

---

## Current caveats

The endpoint responds, but does not yet do what it says:

1. **Nothing is persisted.** `CreateOrderUseCase.execute` logs the command and returns
   `new CreateOrderResult(UUID.randomUUID())`. The returned `orderId` is random and refers to
   no stored order.
2. **`orderAddress` is dropped.** `OrderWebMapper` maps `OrderCreateRequest` →
   `CreateOrderCommand`, but the request field is `orderAddress` while the command field is
   `deliveryAddress`. MapStruct matches by name, so `deliveryAddress` arrives `null`.
3. **Item `subTotal` is dropped.** Same cause: request `subTotal` vs. command `subtotal`
   (lowercase `t`).
4. **No domain validation runs.** The `Order` aggregate is never constructed, so
   `validateOrder()` never executes — mismatched prices are accepted.
5. **No read endpoints.** There is no `GET /api/v1/orders/{id}`. The controller is named
   `OrderCommandController` in anticipation of a query-side counterpart.

Fixes are tracked in
[current-state-and-next-steps.md](../guides/current-state-and-next-steps.md).

---

## Versioning

The path is versioned (`/api/v1/`). Breaking changes to the request or response shape get a new
path segment rather than a modification in place; the `restapi` DTOs exist precisely so the
wire format can be versioned independently of `CreateOrderCommand` and the `Order` aggregate.
