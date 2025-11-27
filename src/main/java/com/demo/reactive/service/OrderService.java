package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import com.demo.reactive.repository.InMemoryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final InMemoryOrderRepository orderRepository;
    private final OrderPublisherService publisherService;

    public Mono<Order> createOrder(Order orderRequest) {
        Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .customerId(orderRequest.getCustomerId())
                .productName(orderRequest.getProductName())
                .quantity(orderRequest.getQuantity())
                .totalAmount(orderRequest.getTotalAmount())
                .status(OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();

        return orderRepository.save(order)
                .flatMap(saved -> publisherService.publishOrder(saved)
                        .thenReturn(saved))
                .doOnSuccess(o -> log.info("Created order {}", o.getId()));
    }

    public Mono<Order> findById(String id) {
        return orderRepository.findById(id);
    }

    public Flux<Order> findAll() {
        return orderRepository.findAll();
    }
}
