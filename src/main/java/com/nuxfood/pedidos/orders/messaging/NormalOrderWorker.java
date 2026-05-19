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
public class NormalOrderWorker {

    private final OrderRepository orderRepository;

    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.orders-queue-url}")
    public void processOrder(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);
            log.info("Processing order: {}", order.getOrderId());

            //throw new RuntimeException("Simulated failure — testing DLQ");
            // simula o processamento onde o status vai ser atualizado para Confirmed
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.CONFIRMED);

            log.info("Order processed successfully: {}", order.getOrderId());

        }catch (Exception e) {
            // lançar exception faz com que a mensagem volte para a queue
            // após 3 tentativas vai para DLQ automaticamente
            log.error("Failed to process order: {}", message, e);
            throw new RuntimeException("Failed to process order", e);
        }
    }

}
