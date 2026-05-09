# NuxFood API 🍕

A backend API for a food delivery platform built with Java 21, Spring Boot, and AWS services. This project was designed to explore and practice cloud-native development with real AWS infrastructure.

---

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3.5**
- **AWS DynamoDB** — order persistence
- **Maven**

## ☁️ AWS Services

| Service | Usage |
|---|---|
| DynamoDB | Order storage with GSI for user queries |

> More services will be added incrementally: SQS, SNS, S3, Lambda.

---

## 📦 Features

### Orders
- `POST /orders` — Create a new order
- `GET /orders` — List all orders with pagination
- `GET /orders/{id}` — Find order by ID
- `GET /orders/user/{userId}` — Find all orders by user
- `GET /orders/user/{userId}/status/{status}` — Find orders by user filtered by status
- `PATCH /orders/{id}/status` — Update order status

### Order Status Flow
```
CREATED → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
                                                    ↘ CANCELLED
CREATED → PAID
```

---

## 🗄️ DynamoDB Design

**Table: `orders`**

| Attribute | Type | Role |
|---|---|---|
| `orderId` | String | Partition Key |
| `userId` | String | GSI Partition Key |
| `product` | String | — |
| `total` | Number | — |
| `status` | String (Enum) | — |
| `createdAt` | String (ISO DateTime) | — |

**GSI: `userId-index`**
- Allows efficient queries by user without scanning the full table
- Projection: ALL




## ⚙️ Running Locally

### Prerequisites
- Java 21
- Maven
- AWS CLI configured with a named profile

### AWS Setup
1. Create a DynamoDB table named `orders` with `orderId` as Partition Key
2. Create a GSI `userId-index` with `userId` as Partition Key and `ALL` projection
3. Configure your AWS credentials:

```bash
aws configure --profile pessoal
```

### application.yml
```yaml
spring:
  application:
    name: nuxfood-api

aws:
  region: us-east-1
  profile: pessoal

server:
  port: 8080
```

### Run
```bash
./mvnw spring-boot:run
```


## 🗺️ Roadmap

- [x] DynamoDB — order persistence and GSI queries
- [x] Pagination with LastEvaluatedKey
- [x] Filter by user and status
- [ ] SQS — async order processing
- [ ] SNS — fan-out to multiple queues
- [ ] Lambda — notification trigger
- [ ] S3 — order receipt upload
- [ ] Kubernetes manifests for EKS deployment

---

