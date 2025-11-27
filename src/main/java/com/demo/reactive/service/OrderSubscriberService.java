package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.demo.reactive.repository.InMemoryOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.reactive.PubSubReactiveFactory;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class OrderSubscriberService {

    private final InMemoryOrderRepository orderRepository;
    private final BigQueryService bigQueryService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private PubSubReactiveFactory pubSubReactiveFactory;

    @Value("${pubsub.subscription.orders:}")
    private String subscriptionName;

    public OrderSubscriberService(
            InMemoryOrderRepository orderRepository,
            BigQueryService bigQueryService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.bigQueryService = bigQueryService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startSubscription() {
        if (pubSubReactiveFactory != null && subscriptionName != null && !subscriptionName.isEmpty()) {
            log.info("Starting reactive subscription on {}", subscriptionName);
            pubSubReactiveFactory.poll(subscriptionName, 1000)
                    .flatMap(msg -> processMessage(msg)
                            .doOnSuccess(order -> msg.ack())
                            .doOnError(e -> {
                                log.error("Error processing message", e);
                                msg.nack();
                            })
                            .onErrorResume(e -> Mono.empty()))
                    .subscribe();
        } else {
            log.warn("PubSub not configured, skipping subscription");
        }
    }

    public Mono<Order> processMessage(AcknowledgeablePubsubMessage message) {
        return Mono.fromCallable(() -> {
                    String json = message.getPubsubMessage().getData().toStringUtf8();
                    log.info("Received message: {}", json);
                    return objectMapper.readValue(json, Order.class);
                })
                .flatMap(order -> {
                    log.info("Processing order {}", order.getId());
                    return orderRepository.updateStatus(order.getId(), OrderStatus.PROCESSING)
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("Order {} not found, using message data", order.getId());
                                return Mono.just(order);
                            }))
                            .flatMap(this::processOrder)
                            .flatMap(processed -> orderRepository.updateStatus(processed.getId(), OrderStatus.COMPLETED)
                                    .defaultIfEmpty(processed))
                            .flatMap(completed -> bigQueryService.insertOrder(completed).thenReturn(completed));
                });
    }

    private Mono<Order> processOrder(Order order) {
        return Mono.fromCallable(() -> {
            log.info("Processing business logic for order {}", order.getId());
            return order;
        });
    }
}
