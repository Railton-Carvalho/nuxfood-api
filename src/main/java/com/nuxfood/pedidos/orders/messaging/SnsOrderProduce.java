package com.nuxfood.pedidos.orders.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuxfood.pedidos.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnsOrderProduce {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.orders-topic-arn}")
    private String ordersTopicArn;

    public void publishOrder(Order order) {
        try {
            String sqsMessage = objectMapper.writeValueAsString(order);

            String emailMessage = "NuxFood: Novo pedido criado!\n\n" +
                    "ID: " + order.getOrderId() + "\n" +
                    "Cliente: " + order.getUserId() + "\n" +
                    "Produto: " + order.getProduct() + "\n" +
                    "Total: R$" + order.getTotal() + "\n" +
                    "Tipo: " + order.getOrderType() + "\n" +
                    "Status: " + order.getStatus() + "\n" +
                    "Data: " + order.getCreatedAt();

            Map<String, String> messageByProtocol = Map.of(
                    "default", sqsMessage,
                    "sqs",     sqsMessage,
                    "email",   emailMessage
            );

            MessageAttributeValue orderTypeAttribute = MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue(order.getOrderType().name())
                    .build();

            PublishRequest pubRequest = PublishRequest.builder()
                    .topicArn(ordersTopicArn)
                    .messageStructure("json") // diz ao SNS que é um mapa de protocolo
                    .message(objectMapper.writeValueAsString(messageByProtocol))
                    .messageAttributes(Map.of("orderType", orderTypeAttribute))
                    .build();

            log.info("Data {}", pubRequest);

            snsClient.publish(pubRequest);

            log.info("Order published to SNS topic with orderType={}: {}",order.getOrderType(), order.getOrderId());
        }catch (JsonProcessingException e) {
            log.error("Failed to serialize order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to serialize order: " + order.getOrderId(), e);

        } catch (Exception e) {
            log.error("Failed to publish order to SNS: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to publish order: " + order.getOrderId(), e);
        }
    }
}
