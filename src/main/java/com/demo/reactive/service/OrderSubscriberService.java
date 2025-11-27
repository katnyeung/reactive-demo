package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.demo.reactive.repository.InMemoryOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.reactive.PubSubReactiveFactory;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class OrderSubscriberService {

    private final InMemoryOrderRepository orderRepository;
    private final BigQueryService bigQueryService;
    private final ObjectMapper objectMapper;
    private PubSubReactiveFactory pubSubReactiveFactory;
    private String subscriptionName;

    public OrderSubscriberService(
            InMemoryOrderRepository orderRepository,
            BigQueryService bigQueryService,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.bigQueryService = bigQueryService;
        this.objectMapper = objectMapper;
    }

    public void setPubSubReactiveFactory(PubSubReactiveFactory factory) {
        this.pubSubReactiveFactory = factory;
    }

    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    @PostConstruct
    public void startSubscription() {
        if (pubSubReactiveFactory != null && subscriptionName != null) {
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
                            .then(processOrder(order))
                            .flatMap(processed -> orderRepository.updateStatus(order.getId(), OrderStatus.COMPLETED))
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
