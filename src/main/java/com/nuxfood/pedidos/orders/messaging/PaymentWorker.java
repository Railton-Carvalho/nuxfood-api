package com.nuxfood.pedidos.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.CancellationReason;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.orders.cancellation.OrderCancellationService;
import com.nuxfood.pedidos.orders.payment.PaymentSimulator;
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
public class PaymentWorker {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final SqsTemplate sqsTemplate;
    private final PaymentSimulator paymentSimulator;
    private final OrderCancellationService orderCancellationService;

    @Value("${aws.sqs.orders-delivery-queue-url}")
    private String deliveryQueueUrl;

    @SqsListener("${aws.sqs.orders-payment-queue-url}")
    public void processPayment(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing payment for order: {}", order.getOrderId());

            TimeUnit.SECONDS.sleep(3);

            if (!paymentSimulator.charge(order)) {
                orderCancellationService.cancel(order.getOrderId(), CancellationReason.PAYMENT_FAILED);
                return;
            }

            orderRepository.updateStatus(order.getOrderId(), OrderStatus.PAID);
            log.info("Payment confirmed for order: {}", order.getOrderId());

            publishToDelivery(order);

        } catch (Exception e) {
            log.error("Failed to process payment: {}", message, e);
            throw new RuntimeException("Failed to process payment", e);
        }
    }

    private void publishToDelivery(Order order) {
        try {
            order.setStatus(OrderStatus.WAITING_FOR_DELIVERY);
            String payload = objectMapper.writeValueAsString(order);
            sqsTemplate.send(deliveryQueueUrl, payload);
            log.info("Order published to delivery: {}", order.getOrderId());
        } catch (Exception e) {
            log.error("Payment confirmed but delivery not published for order: {}", order.getOrderId(), e);
            orderCancellationService.cancelFromMessage(order.getOrderId(), CancellationReason.SYSTEM_ERROR);
        }
    }
}
