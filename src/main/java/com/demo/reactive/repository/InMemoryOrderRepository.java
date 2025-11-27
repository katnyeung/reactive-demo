package com.demo.reactive.repository;

import com.demo.reactive.model.Order;
import com.demo.reactive.model.OrderStatus;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository {

    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    public Mono<Order> save(Order order) {
        return Mono.fromCallable(() -> {
            orders.put(order.getId(), order);
            return order;
        });
    }

    public Mono<Order> findById(String id) {
        return Mono.justOrEmpty(orders.get(id));
    }

    public Flux<Order> findAll() {
        return Flux.fromIterable(orders.values());
    }

    public Mono<Order> updateStatus(String id, OrderStatus status) {
        return Mono.fromCallable(() -> {
            Order order = orders.get(id);
            if (order != null) {
                order.setStatus(status);
                order.setUpdatedAt(LocalDateTime.now());
                orders.put(id, order);
                return order;
            }
            return null;
        }).flatMap(order -> order != null ? Mono.just(order) : Mono.empty());
    }
}
