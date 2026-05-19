package com.nuxfood.pedidos.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.repository.OrderRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressOrderWorker {

    private final OrderRepository orderRepository;

    private final SqsTemplate sqsTemplate;

    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.orders-payment-queue-url}")
    private String payment_queue_url;

    @SqsListener("${aws.sqs.orders-express-queue-url}")
    public void processExpressOrder(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing EXPRESS order with priority: {}", order.getOrderId());

            log.info("EXPRESS ORDER RECEIVED...: " + order.getOrderId());
            TimeUnit.SECONDS.sleep(3);

            //pedido expresso - vai direto para PREPARING
            // pula o CONFIRMED
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.PREPARING);
            order.setStatus(OrderStatus.PREPARING);

            sqsTemplate.send(payment_queue_url, objectMapper.writeValueAsString(order));

            log.info("Express order moved to PREPARING: {}", order.getOrderId());

        }catch (Exception e){
            log.error("Failed to process express order: {}", message, e);
            throw new RuntimeException("Failed to process express order", e);
        }
    }
}
