# Reactive Order Processing Demo

Spring Boot application demonstrating reactive programming with Google Pub/Sub and BigQuery (mocked).

## Tech Stack
- Spring Boot 3.2.x / WebFlux
- Project Reactor (Mono/Flux)
- Google Cloud Pub/Sub (emulator)
- BigQuery (mocked in-memory)

## Prerequisites
- Java 17+
- Maven
- Docker (for Pub/Sub emulator)

## Quick Start

```bash
# Start Pub/Sub emulator
docker-compose up -d

# Create topic and subscription (wait for emulator to start)
curl -X PUT http://localhost:8085/v1/projects/demo-project/topics/orders-topic
curl -X PUT http://localhost:8085/v1/projects/demo-project/subscriptions/orders-subscription \
  -H "Content-Type: application/json" \
  -d '{"topic":"projects/demo-project/topics/orders-topic"}'

# Run tests
mvn test

# Run application
mvn spring-boot:run
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/orders | Create new order |
| GET | /api/orders/{id} | Get order by ID |
| GET | /api/orders | Get all orders |
| GET | /api/orders/stream | SSE stream of orders |

## Test Order Creation

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"C001","productName":"Widget","quantity":5,"totalAmount":99.99}'
```

## Architecture

```
POST /api/orders → OrderController → OrderPublisherService → Pub/Sub Topic
                                                                   ↓
                                                       OrderSubscriberService
                                                          (reactive poll)
                                                                   ↓
                                              ┌────────────────────┴────────────────────┐
                                              ↓                                         ↓
                                   InMemoryOrderRepository                    BigQueryService (mock)
```
