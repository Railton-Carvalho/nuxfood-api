package com.nuxfood.pedidos.model;

import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.model.enums.OrderType;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private String orderId;
    private String userId;
    private String product;
    private Double total;
    private OrderStatus status;
    private OrderType orderType = OrderType.NORMAL;
    private LocalDateTime createdAt;
}