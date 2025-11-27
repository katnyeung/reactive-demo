package com.demo.reactive.repository;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

class InMemoryOrderRepositoryTest {

    private InMemoryOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOrderRepository();
    }

    @Test
    void save_shouldStoreOrder() {
        Order order = createTestOrder("order-1");

        StepVerifier.create(repository.save(order))
                .expectNextMatches(saved -> saved.getId().equals("order-1"))
                .verifyComplete();
    }

    @Test
    void findById_shouldReturnOrder_whenExists() {
        Order order = createTestOrder("order-2");
        repository.save(order).block();

        StepVerifier.create(repository.findById("order-2"))
                .expectNextMatches(found -> found.getCustomerId().equals("C001"))
                .verifyComplete();
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        StepVerifier.create(repository.findById("non-existent"))
                .verifyComplete();
    }

    @Test
    void findAll_shouldReturnAllOrders() {
        repository.save(createTestOrder("order-3")).block();
        repository.save(createTestOrder("order-4")).block();

        StepVerifier.create(repository.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void updateStatus_shouldChangeOrderStatus() {
        Order order = createTestOrder("order-5");
        repository.save(order).block();

        StepVerifier.create(repository.updateStatus("order-5", OrderStatus.COMPLETED))
                .expectNextMatches(updated -> updated.getStatus() == OrderStatus.COMPLETED)
                .verifyComplete();
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
