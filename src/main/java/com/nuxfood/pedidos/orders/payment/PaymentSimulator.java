package com.nuxfood.pedidos.orders.payment;

import com.nuxfood.pedidos.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentSimulator {

    @Value("${nuxfood.orders.payment.fail-when-total-ends-with:99}")
    private int failureSuffix;

    public boolean charge(Order order) {
        if (order.getTotal() == null) {
            log.warn("Payment rejected: order {} has no total", order.getOrderId());
            return false;
        }

        long cents = Math.round(order.getTotal() * 100);
        boolean approved = cents % 100 != failureSuffix;

        if (!approved) {
            log.warn("Payment rejected for order {} (simulated failure, total={})",
                    order.getOrderId(), order.getTotal());
        }

        return approved;
    }
}
