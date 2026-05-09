package com.nuxfood.pedidos.repository;

import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.PagedResponse;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE = "orders";

    public void save(Order order) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("orderId", AttributeValue.fromS(order.getOrderId()));
        item.put("userId", AttributeValue.fromS(order.getUserId()));
        item.put("product", AttributeValue.fromS(order.getProduct()));
        item.put("total", AttributeValue.fromN(order.getTotal().toString()));
        item.put("status", AttributeValue.fromS(order.getStatus().name()));
        item.put("createdAt", AttributeValue.fromS(order.getCreatedAt().toString()));

        dynamoDbClient.putItem(r -> r.tableName(TABLE).item(item));
    }


    // Query via GSI - Find By User
    public Optional<Order> findById(String orderId) {
        GetItemResponse response = dynamoDbClient.getItem(r -> r
                .tableName(TABLE)
                .key(Map.of("orderId", AttributeValue.fromS(orderId)))
        );

        if (!response.hasItem()) return Optional.empty();
        return Optional.of(mapToOrder(response.item()));
    }

    // Find User by id
    public List<Order> findByUserId(String userId) {
        QueryResponse response = dynamoDbClient.query(r -> r
                .tableName(TABLE)
                .indexName("userId-index")
                .keyConditionExpression("userId = :uid")
                .expressionAttributeValues(
                        Map.of(":uid", AttributeValue.fromS(userId))// mapeia os atributos usados nas expressions
                )
        );

        return response.items().stream()
                .map(this::mapToOrder)
                .collect(Collectors.toList());
    }

    // Find all - scan lento | evitar em production
    public List<Order> findAll(){
        ScanResponse response = dynamoDbClient.scan(r -> r.tableName(TABLE));
        return response.items().stream()
                .map(this::mapToOrder)
                .collect(Collectors.toList());
    }

    public void updateStatus(String orderId, OrderStatus newStatus) {
        dynamoDbClient.updateItem(r -> r
                .tableName(TABLE)
                .key(Map.of("orderId", AttributeValue.fromS(orderId)))
                .updateExpression("SET #s = :status")
                .expressionAttributeNames(Map.of("#s", "status"))  // ← define o alias #s
                .expressionAttributeValues(
                        Map.of(":status", AttributeValue.fromS(newStatus.name()))
                )
        );
    }

    // Find All Paginado -Scan com paginação
    public PagedResponse<Order> findAllPaged(int limit, String lastKey){

        ScanRequest.Builder scanBuilder = ScanRequest.builder()
                .tableName(TABLE)
                .limit(limit); // quantos itens por pagina

        // ser veio um lastKey, continua de onde parou
        if(lastKey != null && !lastKey.isEmpty()){
            scanBuilder.exclusiveStartKey(
                    Map.of("orderId", AttributeValue.fromS(lastKey))
            );
        }

        ScanResponse response = dynamoDbClient.scan(scanBuilder.build());

        List<Order> orders = response.items().stream()
                .map(this::mapToOrder)
                .collect(Collectors.toList());
        
        // pega o lastEvaluatedKey para mandar na resposta
        // se for null significa que é a última página
        String nextKey = null;
        if (response.lastEvaluatedKey() != null
                && !response.lastEvaluatedKey().isEmpty()) {
            AttributeValue lastOrderId = response.lastEvaluatedKey().get("orderId");
            if (lastOrderId != null) {
                nextKey = lastOrderId.s();
            }
        }


        return PagedResponse.<Order>builder()
                .items(orders)
                .nextKey(nextKey)
                .count(orders.size())
                .build();

    }

    private Order mapToOrder(Map<String, AttributeValue> item) {
        return Order.builder()
                .orderId(attrS(item, "orderId"))
                .userId(attrS(item, "userId"))
                .product(attrS(item, "product"))
                .total(parseTotal(item.get("total")))
                .status(parseStatus(item.get("status")))
                .createdAt(parseCreatedAt(item.get("createdAt")))
                .build();
    }

    private static String attrS(Map<String, AttributeValue> item, String key) {
        AttributeValue av = item.get(key);
        String s = av != null ? av.s() : null;
        return s != null ? s : "";
    }

    private static double parseTotal(AttributeValue av) {
        if (av == null || av.n() == null) return 0.0;
        return Double.parseDouble(av.n());
    }

    private static OrderStatus parseStatus(AttributeValue av) {
        String s = av != null ? av.s() : null;
        if (s == null || s.isEmpty()) return OrderStatus.CREATED;
        return OrderStatus.valueOf(s);
    }

    private static LocalDateTime parseCreatedAt(AttributeValue av) {
        String s = av != null ? av.s() : null;
        if (s == null || s.isEmpty()) {
            return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        return LocalDateTime.parse(s);
    }

}
