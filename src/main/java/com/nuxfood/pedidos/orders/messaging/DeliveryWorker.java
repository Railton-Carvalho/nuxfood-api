package com.nuxfood.pedidos.orders.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.CancellationReason;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.orders.cancellation.OrderCancellationService;
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
    private final OrderCancellationService orderCancellationService;

    @SqsListener("${aws.sqs.orders-delivery-queue-url}")
    public void processDeliveryOrder(String message) {
        try{
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing delivery for order: {}", order.getOrderId());

            if (hasNoDeliveryDriver(order)) {
                orderCancellationService.cancel(order.getOrderId(), CancellationReason.DELIVERY_FAILED);
                return;
            }

            orderRepository.updateStatus(order.getOrderId(), OrderStatus.OUT_FOR_DELIVERY);

            log.info("Order out for delivery: {}", order.getOrderId());

        } catch (Exception e) {
            log.error("Failed to process delivery: {}", message, e);
            throw new RuntimeException("Failed to process delivery", e);
        }
    }

    private boolean hasNoDeliveryDriver(Order order) {
        return order.getProduct() != null
                && order.getProduct().toUpperCase().contains("SEM_ENTREGADOR");
    }
}
