package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPublisherServiceTest {

    @Mock
    private PubSubTemplate pubSubTemplate;

    private OrderPublisherService publisherService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        publisherService = new OrderPublisherService(pubSubTemplate, objectMapper, "orders-topic");
    }

    @Test
    void publishOrder_shouldReturnMessageId_whenSuccessful() {
        Order order = createTestOrder("order-1");
        when(pubSubTemplate.publish(eq("orders-topic"), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-123"));

        StepVerifier.create(publisherService.publishOrder(order))
                .expectNext("msg-123")
                .verifyComplete();
    }

    @Test
    void publishOrder_shouldPropagateError_whenPublishFails() {
        Order order = createTestOrder("order-1");
        when(pubSubTemplate.publish(eq("orders-topic"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Pub/Sub error")));

        StepVerifier.create(publisherService.publishOrder(order))
                .expectError(RuntimeException.class)
                .verify();
    }

    private Order createTestOrder(String id) {
        return Order.builder()
                .id(id)
                .customerId("C001")
                .productName("Widget")
                .quantity(5)
                .totalAmount(99.99)
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
