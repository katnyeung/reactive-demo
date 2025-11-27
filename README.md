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

## Reactive Flow Explanation

### Mono vs Flux - The Basics

| Type | Description | Example in code |
|------|-------------|-----------------|
| **Mono<T>** | 0 or 1 element | `getOrderById()` returns one order |
| **Flux<T>** | 0 to N elements | `getAllOrders()` returns multiple orders |

**Key point**: Nothing happens until someone **subscribes**. These are "lazy" - they define what to do, not when.

### The Complete Flow (Create Order)

```
HTTP POST /api/orders
        ↓
   Controller.createOrder()     ← Returns Mono<Order>
        ↓
   OrderService.createOrder()   ← Builds Order, returns Mono
        ↓
   repository.save()            ← Mono<Order>
        ↓ (flatMap)
   publisherService.publishOrder()  ← Sends to Pub/Sub
        ↓
   Response sent to client

        ═══════════════════════════

   [ASYNC - Subscriber polls Pub/Sub]
        ↓
   processMessage()             ← Parses JSON to Order
        ↓ (flatMap)
   updateStatus(PROCESSING)
        ↓ (then)
   processOrder()               ← Business logic
        ↓ (flatMap)
   updateStatus(COMPLETED)
        ↓ (flatMap)
   bigQueryService.insertOrder()
        ↓
   msg.ack()                    ← Acknowledge message
```

### How the Subscriber Polls

In `OrderSubscriberService.java`:

```java
pubSubReactiveFactory.poll(subscriptionName, 1000)  // ①
    .flatMap(msg -> processMessage(msg)              // ②
        .doOnSuccess(order -> msg.ack())             // ③
        .doOnError(e -> msg.nack())                  // ④
        .onErrorResume(e -> Mono.empty()))           // ⑤
    .subscribe();                                     // ⑥
```

| Step | Code | Explanation |
|------|------|-------------|
| ① | `poll(subscriptionName, 1000)` | Returns `Flux<AcknowledgeablePubsubMessage>`. Polls Pub/Sub every **1000ms**. This is a **hot stream** - keeps polling forever |
| ② | `flatMap(msg -> ...)` | For each message received, run `processMessage()` asynchronously |
| ③ | `doOnSuccess(msg.ack())` | If processing succeeds, **acknowledge** the message (tell Pub/Sub "I got it, delete it") |
| ④ | `doOnError(msg.nack())` | If processing fails, **negative acknowledge** (tell Pub/Sub "redelivery needed") |
| ⑤ | `onErrorResume(Mono.empty())` | Don't break the stream on errors, continue polling |
| ⑥ | `.subscribe()` | **Starts the whole thing**. Without this, nothing happens! |

### Why `.subscribe()` Matters

```java
// This does NOTHING - just a definition
Mono<Order> order = orderService.createOrder(request);

// This TRIGGERS the work
order.subscribe();

// In WebFlux, Spring auto-subscribes when you return Mono/Flux from controller
@GetMapping("/{id}")
public Mono<Order> getOrderById(...) {  // Spring subscribes for you
    return orderService.findById(id);
}
```

### The Polling Mechanism Visualized

```
Time →  0ms    1000ms   2000ms   3000ms   4000ms
        |       |        |        |        |
        poll    poll     poll     poll     poll
        ↓       ↓        ↓        ↓        ↓
        []    [msg1]    []     [msg2,3]   []
               ↓                  ↓↓
           process()         process() x2
               ↓                  ↓↓
            ack()              ack() x2
```

The `PubSubReactiveFactory.poll()` internally:
1. Calls Pub/Sub pull API every 1000ms
2. Wraps received messages into a `Flux`
3. Emits each message downstream
4. Keeps running indefinitely (until app stops)

### Operators Used

| Operator | Purpose | Usage |
|----------|---------|-------|
| `flatMap` | Transform + flatten async results | Chain multiple async operations |
| `then` | Ignore result, continue with next | After updateStatus, run processOrder |
| `thenReturn` | Ignore result, return specific value | After publish, return saved order |
| `doOnSuccess` | Side effect on success | Logging, ack() |
| `doOnError` | Side effect on error | Logging, nack() |
| `onErrorResume` | Replace error with fallback | Continue stream on error |

The subscriber runs forever in the background (started at `@PostConstruct`), polling every second, while the controller handles HTTP requests independently. Both are non-blocking and reactive.
