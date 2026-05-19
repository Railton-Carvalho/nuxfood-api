package com.nuxfood.pedidos.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.repository.OrderRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpressOrderWorker {

    private final OrderRepository orderRepository;

    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.orders-express-queue-url}")
    public void processExpressOrder(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing EXPRESS order with priority: {}", order.getOrderId());

            //pedido expresso - vai direto para PREPARING
            // pula o CONFIRMED
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.PREPARING);

            log.info("Express order moved to PREPARING: {}", order.getOrderId());

        }catch (Exception e){
            log.error("Failed to process express order: {}", message, e);
            throw new RuntimeException("Failed to process express order", e);
        }
    }
}
