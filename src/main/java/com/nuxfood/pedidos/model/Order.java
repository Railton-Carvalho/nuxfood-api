package com.nuxfood.pedidos.model;

import com.nuxfood.pedidos.model.enums.CancellationReason;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.model.enums.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Pedido")
public class Order {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String orderId;
    @Schema(example = "user-123")
    private String userId;
    @Schema(example = "Pizza Margherita")
    private String product;
    @Schema(example = "49.90")
    private Double total;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private OrderStatus status;
    @Schema(example = "NORMAL")
    private OrderType orderType = OrderType.NORMAL;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private CancellationReason cancelReason;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime cancelledAt;
}