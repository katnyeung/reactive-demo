package com.demo.reactive.controller;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.demo.reactive.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private OrderService orderService;

    @Test
    void createOrder_shouldReturnCreatedOrder() {
        Order order = createTestOrder("order-1");
        when(orderService.createOrder(any(Order.class))).thenReturn(Mono.just(order));

        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "customerId": "C001",
                        "productName": "Widget",
                        "quantity": 5,
                        "totalAmount": 99.99
                    }
                    """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.customerId").isEqualTo("C001")
                .jsonPath("$.status").isEqualTo("CREATED");
    }

    @Test
    void getOrderById_shouldReturnOrder_whenExists() {
        Order order = createTestOrder("order-1");
        when(orderService.findById("order-1")).thenReturn(Mono.just(order));

        webTestClient.get()
                .uri("/api/orders/order-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("order-1");
    }

    @Test
    void getOrderById_shouldReturn404_whenNotExists() {
        when(orderService.findById("non-existent")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/orders/non-existent")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getAllOrders_shouldReturnAllOrders() {
        when(orderService.findAll()).thenReturn(Flux.just(
                createTestOrder("order-1"),
                createTestOrder("order-2")
        ));

        webTestClient.get()
                .uri("/api/orders")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Order.class)
                .hasSize(2);
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
