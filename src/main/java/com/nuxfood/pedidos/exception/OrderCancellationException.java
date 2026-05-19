package com.nuxfood.pedidos.exception;

public class OrderCancellationException extends RuntimeException {

    public OrderCancellationException(String message) {
        super(message);
    }
}
