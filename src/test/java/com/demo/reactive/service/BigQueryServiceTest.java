package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

class BigQueryServiceTest {

    private BigQueryService bigQueryService;

    @BeforeEach
    void setUp() {
        bigQueryService = new BigQueryService();
    }

    @Test
    void insertOrder_shouldStoreOrderForAnalytics() {
        Order order = createTestOrder("order-1");

        StepVerifier.create(bigQueryService.insertOrder(order))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getOrderCount_shouldReturnCorrectCount() {
        bigQueryService.insertOrder(createTestOrder("order-1")).block();
        bigQueryService.insertOrder(createTestOrder("order-2")).block();

        StepVerifier.create(bigQueryService.getOrderCount())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void getTotalRevenue_shouldSumAllOrderAmounts() {
        Order order1 = createTestOrder("order-1");
        order1.setTotalAmount(100.0);
        Order order2 = createTestOrder("order-2");
        order2.setTotalAmount(50.0);

        bigQueryService.insertOrder(order1).block();
        bigQueryService.insertOrder(order2).block();

        StepVerifier.create(bigQueryService.getTotalRevenue())
                .expectNext(150.0)
                .verifyComplete();
    }

    private Order createTestOrder(String id) {
        return Order.builder()
                .id(id)
                .customerId("C001")
                .productName("Widget")
                .quantity(5)
                .totalAmount(99.99)
                .status(OrderStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
