package com.nuxfood.pedidos.mapper;

import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class OrderDynamoMapper {

    public Order toOrder(Map<String, AttributeValue> item) {
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
