package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
public class OrderPublisherService {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;

    public OrderPublisherService(
            @Autowired(required = false) PubSubTemplate pubSubTemplate,
            ObjectMapper objectMapper,
            @Value("${pubsub.topic.orders}") String topicName) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
    }

    public Mono<String> publishOrder(Order order) {
        if (pubSubTemplate == null) {
            log.warn("PubSub not available, skipping publish for order {}", order.getId());
            return Mono.just("skipped");
        }
        return Mono.fromCallable(() -> serializeOrder(order))
                .flatMap(json -> {
                    log.info("Publishing order {} to topic {}", order.getId(), topicName);
                    return Mono.fromFuture(pubSubTemplate.publish(topicName, json));
                })
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(msgId -> log.info("Published order {} with messageId {}", order.getId(), msgId))
                .doOnError(e -> log.error("Failed to publish order {}: {}", order.getId(), e.getMessage()))
                .onErrorReturn("publish-skipped");
    }

    private String serializeOrder(Order order) throws JsonProcessingException {
        return objectMapper.writeValueAsString(order);
    }
}
