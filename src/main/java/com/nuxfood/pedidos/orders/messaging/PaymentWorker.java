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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWorker {
    private final OrderRepository orderRepository;

    private final ObjectMapper objectMapper;

    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.orders-payment-queue-url}")
    private String paymentQueueUrl;

    @Value("${aws.sqs.orders-delivery-queue-url}")
    private String deliveryQueueUrl;

    @SqsListener("${aws.sqs.orders-payment-queue-url}")
    public void processPayment(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing payment for order: {}", order.getOrderId());

            //simula processamento de pagamento
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.PAID);
            log.info("Payment confirmed for order: {}", order.getOrderId());

            log.info("Avaliating Payment for order: " + order.getOrderId());
            TimeUnit.SECONDS.sleep(3);

            publishToDelivery(order);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process Payment",e);
        }
    }

    private void publishToDelivery(Order order) {
        try {
            order.setStatus(OrderStatus.WAITING_FOR_DELIVERY);

            String message = objectMapper.writeValueAsString(order);
            sqsTemplate.send(deliveryQueueUrl, message);

            log.info("Order published to delivery: {}", order.getOrderId());
        } catch (Exception e) {
            // falha crítica - pagamento foi confimado mas a entrega não foi publicada
            log.error("CRITICAL: Payment confirmed but delivery "+"not published for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to publish to delivery",e);
        }
    }

}
