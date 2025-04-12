package edu.neu.info7255.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String PLAN_EXCHANGE = "plan-exchange";
    public static final String PLAN_QUEUE    = "plan-queue";

    @Bean
    Queue planQueue() { return QueueBuilder.durable(PLAN_QUEUE).build(); }
    @Bean Exchange planEx() { return ExchangeBuilder.topicExchange(PLAN_EXCHANGE).durable(true).build(); }
    @Bean Binding binding() { return BindingBuilder.bind(planQueue()).to(planEx()).with("#").noargs(); }
}