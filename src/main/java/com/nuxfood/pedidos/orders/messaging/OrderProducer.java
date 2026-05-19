package com.nuxfood.pedidos.orders.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.OrderType;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProducer {

    private final SqsTemplate sqsTemplate;

    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.orders-queue-url}")
    private String ordersQueueUrl;

    @Value("${aws.sqs.orders-express-queue-url}")
    private String ordersExpressQueueUrl;

    public void publishOrder(Order order){
        try {
            String message = objectMapper.writeValueAsString(order);

            String queueUrl = OrderType.EXPRESS.equals(order.getOrderType())?
                    ordersExpressQueueUrl
                    : ordersQueueUrl;
            log.info("Analizing Order Data for progress...: " + order.getOrderId());
            TimeUnit.SECONDS.sleep(5);

            sqsTemplate.send(queueUrl, message);

            log.info("Order published to SQS: {}", order.getOrderId());
        }catch (JsonProcessingException e) {
            log.error("Failed to serialize order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to serialize order", e);
        }catch (Exception e){
            log.error("Failed to publish order to SQS: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to publish order to queue", e);
        }
    }
}
