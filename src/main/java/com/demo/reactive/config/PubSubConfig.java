package com.demo.reactive.config;

import com.demo.reactive.service.OrderSubscriberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.spring.pubsub.reactive.PubSubReactiveFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubConfig {

    @Value("${pubsub.subscription.orders}")
    private String subscriptionName;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public OrderSubscriberService orderSubscriberService(
            OrderSubscriberService subscriberService,
            PubSubReactiveFactory pubSubReactiveFactory) {
        subscriberService.setPubSubReactiveFactory(pubSubReactiveFactory);
        subscriberService.setSubscriptionName(subscriptionName);
        return subscriberService;
    }
}
