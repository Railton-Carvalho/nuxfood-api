# NuxFood API 🍕

A backend API for a food delivery platform built with Java 21, Spring Boot, and AWS services. This project was designed to explore and practice cloud-native development with real AWS infrastructure, incrementally adding services day by day.

---

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3.5**
- **Maven**
- **AWS SDK v2**
- **Spring Cloud AWS 3.1**
- **SpringDoc OpenAPI** (Swagger UI)

## ☁️ AWS Services

| Service | Usage | Status |
|---|---|---|
| DynamoDB | Order storage with GSI for user queries | ✅ Implemented |
| SQS | Async order processing with multiple queues and DLQ | ✅ Implemented |
| CloudWatch | Queue monitoring and alarms | ✅ Implemented |
| SNS | Fan-out with filter policy routing | 🔜 Next |
| Lambda | Notification trigger | 🔜 Next |
| S3 | Order receipt upload | 🔜 Next |

---

## 📦 Features

### Orders API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/orders` | Create a new order and publish to SQS |
| `GET` | `/orders` | List all orders |
| `GET` | `/orders/paged` | List all orders with pagination |
| `GET` | `/orders/{id}` | Find order by ID |
| `GET` | `/orders/user/{userId}` | Find all orders by user |
| `GET` | `/orders/user/{userId}/status/{status}` | Find orders by user filtered by status |
| `PATCH` | `/orders/{id}/status` | Update order status |
| `POST` | `/orders/{id}/cancel` | Cancel order (optional `reason` query param) |

### Swagger / OpenAPI

Interactive API documentation is available when the app is running:

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Endpoints are grouped under the **Pedidos** tag with `@Operation` summaries.

### Order Status Flow

```
NORMAL order (happy path):
CREATED → CONFIRMED → PAID → WAITING_FOR_DELIVERY → OUT_FOR_DELIVERY → DELIVERED

EXPRESS order (happy path):
CREATED → PREPARING → PAID → WAITING_FOR_DELIVERY → OUT_FOR_DELIVERY → DELIVERED

Any stage (except DELIVERED) ──► CANCELLED
```

### Order Types

| Type | Queue | Worker | Next step |
|---|---|---|---|
| `NORMAL` | `orders-queue` | `NormalOrderWorker` | `CONFIRMED` → payment queue |
| `EXPRESS` | `orders-express-queue` | `ExpressOrderWorker` | `PREPARING` → payment queue |

### Cancellation

Cancellation is centralized in `OrderCancellationService`. Orders in status `DELIVERED` cannot be cancelled. Requests on already `CANCELLED` orders are idempotent.

| Reason | Trigger |
|---|---|
| `PAYMENT_FAILED` | `PaymentSimulator` rejects payment (see below) |
| `DELIVERY_FAILED` | No delivery driver available (product contains `SEM_ENTREGADOR`) |
| `SYSTEM_ERROR` | SQS publish failure after save, or delivery queue publish failure after payment |
| `USER_REQUEST` | Manual `POST /orders/{id}/cancel` (default reason) |
| `PROCESSING_FAILED` | Reserved for future use |

**Payment simulation:** totals whose cents end with the configured suffix are rejected (default: `99` → e.g. `49.99` fails).

**Delivery simulation:** product name containing `SEM_ENTREGADOR` cancels the order during delivery processing.

Cancel request example:

```bash
curl -X POST "http://localhost:8080/orders/{orderId}/cancel?reason=USER_REQUEST"
```

Cancelled orders persist `cancelReason` and `cancelledAt` in DynamoDB.

---

## 🗄️ DynamoDB Design

**Table: `orders`**

| Attribute | Type | Role |
|---|---|---|
| `orderId` | String | Partition Key |
| `userId` | String | GSI Partition Key |
| `product` | String | — |
| `total` | Number | — |
| `status` | String (Enum) | OrderStatus |
| `orderType` | String (Enum) | OrderType |
| `createdAt` | String (ISO DateTime) | — |
| `cancelReason` | String (Enum) | CancellationReason (when cancelled) |
| `cancelledAt` | String (ISO DateTime) | Set on cancellation |

### Operations implemented

| Operation | Method | Use case |
|---|---|---|
| `PutItem` | Save | Create new order |
| `GetItem` | Find by ID | O(1) lookup by orderId |
| `Query` | Find by user | Efficient via GSI userId-index |
| `Scan` | Find all | Full table read — used with pagination |
| `UpdateItem` | Update status | Partial update without overwriting |
| `UpdateItem` | Cancel | Sets status, cancelReason, cancelledAt |
| `ConditionExpression` | Safe update | Only updates if item exists / not delivered |
| `FilterExpression` | Filter by status | Applied after GSI query |
| `LastEvaluatedKey` | Pagination | Safe handling of 1MB DynamoDB limit |

---

## 📨 SQS Architecture

```
POST /orders
     │
     ▼
DynamoDB (save order — CREATED)
     │
     ▼
OrderProducer
     │
     ├── OrderType.NORMAL  ──► orders-queue         ──► NormalOrderWorker  → CONFIRMED
     │                                                        │
     │                                              orders-payment-queue
     │                                                        │
     │                                              PaymentWorker
     │                                                   ├── charge OK  → PAID
     │                                                   │       └──► orders-delivery-queue
     │                                                   │                    │
     │                                                   │             DeliveryWorker → OUT_FOR_DELIVERY
     │                                                   │
     │                                                   └── charge FAIL → CANCELLED (PAYMENT_FAILED)
     │
     └── OrderType.EXPRESS ──► orders-express-queue ──► ExpressOrderWorker → PREPARING
                                                                  │
                                                        orders-payment-queue
                                                                  │
                                                        (same PaymentWorker flow as above)

Failures:
  • OrderProducer SQS publish fail     → CANCELLED (SYSTEM_ERROR)
  • Payment OK, delivery publish fail  → CANCELLED (SYSTEM_ERROR)
  • Delivery: product has SEM_ENTREGADOR → CANCELLED (DELIVERY_FAILED)
  • POST /orders/{id}/cancel             → CANCELLED (USER_REQUEST or custom reason)
```

### Queues

| Queue | Purpose | DLQ |
|---|---|---|
| `orders-queue` | Normal order processing | `orders-queue-dlq` |
| `orders-express-queue` | Express order processing | `orders-queue-dlq` |
| `orders-payment-queue` | Payment processing | `orders-queue-dlq` |
| `orders-delivery-queue` | Delivery allocation | `orders-queue-dlq` |
| `orders-queue-dlq` | Failed messages — max 3 retries | — |

---

## 📊 CloudWatch Monitoring

| Alarm | Metric | Threshold | Meaning |
|---|---|---|---|
| `nuxfood-dlq-messages-alarm` | `ApproximateNumberOfMessagesVisible` on DLQ | ≥ 1 | A message failed 3 times — investigate immediately |
| `nuxfood-queue-backlog-alarm` | `ApproximateNumberOfMessagesVisible` on main queue | ≥ 100 | Worker is slow or stopped |

Both alarms notify via SNS email topic `nuxfood-alerts`.

---

## 📄 Pagination

DynamoDB returns a maximum of 1MB per query. Pagination is implemented using `LastEvaluatedKey`:

```
GET /orders/paged?limit=10                       # first page
GET /orders/paged?limit=10&lastKey={lastOrderId} # next page
```

Response format:
```json
{
  "items": [...],
  "nextKey": "uuid-of-last-item",
  "count": 10
}
```

When `nextKey` is `null`, there are no more pages.

---

## ⚙️ Running Locally

### Prerequisites
- Java 21
- Maven
- AWS CLI configured with a named profile
- AWS account with DynamoDB, SQS and CloudWatch permissions

### AWS Setup

**IAM — attach to your user:**
- `AmazonDynamoDBFullAccess`
- `AmazonSQSFullAccess`

```bash
aws configure --profile pessoal
```

**DynamoDB:**
1. Create table `orders` — Partition Key: `orderId` (String), Capacity: On-demand
2. Create GSI `userId-index` — Partition Key: `userId` (String), Projection: ALL

**SQS:**
1. Create `orders-queue-dlq` — Standard, retention: 14 days
2. Create `orders-queue` — Standard, DLQ: `orders-queue-dlq`, maxReceiveCount: 3
3. Create `orders-express-queue` — Standard, DLQ: `orders-queue-dlq`, maxReceiveCount: 3
4. Create `orders-payment-queue` — Standard, DLQ: `orders-queue-dlq`, maxReceiveCount: 3
5. Create `orders-delivery-queue` — Standard, DLQ: `orders-queue-dlq`, maxReceiveCount: 3

**CloudWatch:**
1. Create alarm `nuxfood-dlq-messages-alarm` — metric: `ApproximateNumberOfMessagesVisible` on `orders-queue-dlq`, threshold ≥ 1
2. Create alarm `nuxfood-queue-backlog-alarm` — metric: `ApproximateNumberOfMessagesVisible` on `orders-queue`, threshold ≥ 100

### application.yml

```yaml
spring:
  application:
    name: nuxfood-api

aws:
  region: us-east-1
  profile: pessoal
  sqs:
    orders-queue-url: https://sqs.us-east-1.amazonaws.com/{accountId}/orders-queue
    orders-express-queue-url: https://sqs.us-east-1.amazonaws.com/{accountId}/orders-express-queue
    orders-payment-queue-url: https://sqs.us-east-1.amazonaws.com/{accountId}/orders-payment-queue
    orders-delivery-queue-url: https://sqs.us-east-1.amazonaws.com/{accountId}/orders-delivery-queue

cloud:
  aws:
    sqs:
      region: us-east-1

nuxfood:
  orders:
    payment:
      fail-when-total-ends-with: 99   # e.g. 49.99 simulates payment failure

server:
  port: 8080

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

### Run

```bash
./mvnw spring-boot:run
```

Open Swagger UI: http://localhost:8080/swagger-ui.html

---

## 🧪 Testing the API

```bash
# Create normal order → CREATED → CONFIRMED → PAID → OUT_FOR_DELIVERY (automatic)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "product": "pizza-margherita",
    "total": 49.90,
    "orderType": "NORMAL"
  }'

# Create express order → CREATED → PREPARING → PAID → ... (automatic)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "product": "Milkshake 500ml Caramelo",
    "total": 69.90,
    "orderType": "EXPRESS"
  }'

# Simulate payment failure (total ends with .99)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "product": "pizza-margherita",
    "total": 49.99,
    "orderType": "NORMAL"
  }'

# Simulate delivery failure (no driver)
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "product": "Pizza SEM_ENTREGADOR",
    "total": 35.00,
    "orderType": "NORMAL"
  }'

# Cancel order manually
curl -X POST "http://localhost:8080/orders/{orderId}/cancel?reason=USER_REQUEST"

# Find by ID
curl http://localhost:8080/orders/{orderId}

# Find by user
curl http://localhost:8080/orders/user/user-123

# Find by user and status
curl http://localhost:8080/orders/user/user-123/status/CANCELLED

# List all with pagination
curl "http://localhost:8080/orders/paged?limit=5"
curl "http://localhost:8080/orders/paged?limit=5&lastKey={lastOrderId}"

# Update status manually
curl -X PATCH "http://localhost:8080/orders/{orderId}/status?status=DELIVERED"
```

---

## 🗺️ Roadmap

- [x] DynamoDB — order persistence and GSI queries
- [x] Pagination with LastEvaluatedKey
- [x] Filter by user and status
- [x] SQS — async order processing with Normal and Express queues
- [x] SQS — payment and delivery pipeline (automatic queue chaining)
- [x] SQS — Dead Letter Queue with maxReceiveCount=3
- [x] CloudWatch — DLQ and backlog alarms
- [x] Swagger / OpenAPI documentation (SpringDoc)
- [x] Order cancellation flow (payment, delivery, system errors, manual API)
- [ ] SNS — fan-out with filter policy routing
- [ ] Lambda — notification trigger via SQS event source
- [ ] S3 — order receipt upload with presigned URL
- [ ] Kubernetes manifests for EKS deployment

---

## 👨‍💻 Author

**Railton Rosa de Carvalho**
Backend Developer · Java · Spring Boot · AWS
[LinkedIn](https://linkedin.com/in/seu-perfil) · [GitHub](https://github.com/seu-usuario)
