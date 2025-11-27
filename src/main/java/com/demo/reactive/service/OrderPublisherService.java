package com.demo.reactive.service;

import com.demo.reactive.model.Order;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class OrderPublisherService {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;

    public OrderPublisherService(
            PubSubTemplate pubSubTemplate,
            ObjectMapper objectMapper,
            @Value("${pubsub.topic.orders}") String topicName) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
    }

    public Mono<String> publishOrder(Order order) {
        return Mono.fromCallable(() -> serializeOrder(order))
                .flatMap(json -> {
                    log.info("Publishing order {} to topic {}", order.getId(), topicName);
                    return Mono.fromFuture(pubSubTemplate.publish(topicName, json));
                })
                .doOnSuccess(msgId -> log.info("Published order {} with messageId {}", order.getId(), msgId));
    }

    private String serializeOrder(Order order) throws JsonProcessingException {
        return objectMapper.writeValueAsString(order);
    }
}
