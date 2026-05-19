package com.nuxfood.pedidos.orders.cancellation;

import com.nuxfood.pedidos.exception.OrderCancellationException;
import com.nuxfood.pedidos.exception.OrderNotFoundException;
import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.enums.CancellationReason;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderRepository orderRepository;

    public Order cancel(String orderId, CancellationReason reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderCancellationException("Pedido já entregue não pode ser cancelado");
        }

        orderRepository.cancel(orderId, reason);
        log.info("Order {} cancelled. reason={}", orderId, reason);

        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    public void cancelFromMessage(String orderId, CancellationReason reason) {
        try {
            cancel(orderId, reason);
        } catch (OrderNotFoundException e) {
            log.warn("Cancel skipped: {}", e.getMessage());
        } catch (OrderCancellationException e) {
            log.warn("Cancel skipped for order {}: {}", orderId, e.getMessage());
        }
    }
}
