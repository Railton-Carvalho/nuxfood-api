package com.nuxfood.pedidos.orders.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.repository.OrderRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryWorker {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.orders-delivery-queue-url}")
    public void processDeliveryOrder(String message) {
        try{
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing delivery for order: {}", order.getOrderId());

            //simula alocação de entregador
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.OUT_FOR_DELIVERY);

            log.info("Order out for delivery: {}", order.getOrderId());

        } catch (Exception e) {
            log.error("Failed to process delivery: {}", message, e);
            throw new RuntimeException("Failed to process delivery", e);
        }
    }
}
