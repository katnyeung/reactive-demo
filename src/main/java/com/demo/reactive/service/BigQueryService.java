package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BigQueryService {

    private final ConcurrentHashMap<String, Order> analyticsStore = new ConcurrentHashMap<>();

    public Mono<Boolean> insertOrder(Order order) {
        return Mono.fromCallable(() -> {
            log.info("[BigQuery Mock] Inserting order {} into analytics", order.getId());
            analyticsStore.put(order.getId(), order);
            return true;
        });
    }

    public Mono<Long> getOrderCount() {
        return Mono.fromCallable(() -> (long) analyticsStore.size());
    }

    public Mono<Double> getTotalRevenue() {
        return Mono.fromCallable(() ->
                analyticsStore.values().stream()
                        .mapToDouble(Order::getTotalAmount)
                        .sum()
        );
    }
}
