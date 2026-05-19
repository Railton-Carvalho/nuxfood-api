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
public class NormalOrderWorker {

    private final OrderRepository orderRepository;

    private final SqsTemplate sqsTemplate;

    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.orders-payment-queue-url}")
    private String paymentQueueUrl;

    @SqsListener("${aws.sqs.orders-queue-url}")
    public void processOrder(String message) {
        try {
            Order order = objectMapper.readValue(message, Order.class);

            log.info("NORMAL ORDER RECEIVED...: " + order.getOrderId());
            TimeUnit.SECONDS.sleep(3);

            //throw new RuntimeException("Simulated failure — testing DLQ");
            // simula o processamento onde o status vai ser atualizado para Confirmed
            orderRepository.updateStatus(order.getOrderId(), OrderStatus.CONFIRMED);
            order.setStatus(OrderStatus.CONFIRMED);

            sqsTemplate.send(paymentQueueUrl, objectMapper.writeValueAsString(order));

            log.info("Order processed successfully: {}", order.getOrderId());

        }catch (Exception e) {
            // lançar exception faz com que a mensagem volte para a queue
            // após 3 tentativas vai para DLQ automaticamente
            log.error("Failed to process order: {}", message, e);
            throw new RuntimeException("Failed to process order", e);
        }
    }

}
