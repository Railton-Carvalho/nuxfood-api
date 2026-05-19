package com.nuxfood.pedidos.repository;

import com.nuxfood.pedidos.mapper.OrderDynamoMapper;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.PagedResponse;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final DynamoDbClient dynamoDbClient;
    private final OrderDynamoMapper orderDynamoMapper;
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
        return Optional.of(orderDynamoMapper.toOrder(response.item()));
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
                .map(orderDynamoMapper::toOrder)
                .collect(Collectors.toList());
    }

    // Find all - scan lento | evitar em production
    public List<Order> findAll(){
        ScanResponse response = dynamoDbClient.scan(r -> r.tableName(TABLE));
        return response.items().stream()
                .map(orderDynamoMapper::toOrder)
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
                .conditionExpression("attribute_exists(orderId)") // só atualiza caso encontre o item
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
                .map(orderDynamoMapper::toOrder)
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

    // Find by User + filter by status - query no GSI com FilterExpression
    public List<Order> findByUserAndStatus(String userId, OrderStatus status) {
        QueryResponse response = dynamoDbClient.query(r -> r
                .tableName(TABLE)
                .indexName("userId-index")
                .keyConditionExpression("userId = :uid")
                .filterExpression("#s = :status")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                        ":uid", AttributeValue.fromS(userId),
                        ":status", AttributeValue.fromS(status.name())
                ))
            );

        return response.items().stream()
                .map(orderDynamoMapper::toOrder)
                .toList();
    }

}
