package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.demo.reactive.repository.InMemoryOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.AcknowledgeablePubsubMessage;
import com.google.pubsub.v1.PubsubMessage;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSubscriberServiceTest {

    @Mock
    private InMemoryOrderRepository orderRepository;

    @Mock
    private BigQueryService bigQueryService;

    @Mock
    private AcknowledgeablePubsubMessage ackMessage;

    private OrderSubscriberService subscriberService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        subscriberService = new OrderSubscriberService(orderRepository, bigQueryService, objectMapper);
    }

    @Test
    void processMessage_shouldUpdateOrderAndStoreInBigQuery() throws Exception {
        Order order = createTestOrder("order-1");
        String orderJson = objectMapper.writeValueAsString(order);

        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(orderJson))
                .build();
        when(ackMessage.getPubsubMessage()).thenReturn(pubsubMessage);

        when(orderRepository.updateStatus(eq("order-1"), eq(OrderStatus.PROCESSING)))
                .thenReturn(Mono.just(order));
        when(orderRepository.updateStatus(eq("order-1"), eq(OrderStatus.COMPLETED)))
                .thenReturn(Mono.just(order));
        when(bigQueryService.insertOrder(any(Order.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(subscriberService.processMessage(ackMessage))
                .expectNextMatches(processed -> processed.getId().equals("order-1"))
                .verifyComplete();

        verify(bigQueryService).insertOrder(any(Order.class));
    }

    @Test
    void processMessage_shouldHandleInvalidJson() {
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8("invalid json"))
                .build();
        when(ackMessage.getPubsubMessage()).thenReturn(pubsubMessage);

        StepVerifier.create(subscriberService.processMessage(ackMessage))
                .expectError()
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
