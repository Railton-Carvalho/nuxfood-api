package com.nuxfood.pedidos.controller;

import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.PagedResponse;
import com.nuxfood.pedidos.model.enums.CancellationReason;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.orders.cancellation.OrderCancellationService;
import com.nuxfood.pedidos.orders.messaging.OrderProducer;
import com.nuxfood.pedidos.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "Pedidos", description = "Operações de criação e consulta de pedidos")
public class OrderController {

    private final OrderRepository repository;

    private final OrderProducer orderProducer;

    private final OrderCancellationService orderCancellationService;

    @Operation(summary = "Criar pedido")
    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Order order) {
        order.setOrderId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());

        // salva no dynamoDB
        repository.save(order);

        // lança na queue SQS
        orderProducer.publishOrder(order);
        log.info("Order created and published: {}", order.getOrderId());

        return ResponseEntity.status(201).body(order);
    }

    @Operation(summary = "Buscar pedido por ID")
    @GetMapping("/{id}")
    public ResponseEntity<Order> findById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Listar pedidos paginados")
    @GetMapping("/paged")
    public PagedResponse<Order> findAll(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String lastKey) {
        return repository.findAllPaged(limit, lastKey);
    }

    @Operation(summary = "Listar pedidos por usuário e status")
    @GetMapping("/user/{userId}/status/{status}")
    public List<Order> findAllByUserAndStatus(
            @PathVariable String userId,
            @PathVariable OrderStatus status
    ){
        return repository.findByUserAndStatus(userId, status);
    }

    @Operation(summary = "Listar pedidos por usuário")
    @GetMapping("/user/{userId}")
    public List<Order> findByUser(@PathVariable String userId) {
        return repository.findByUserId(userId);
    }

    @Operation(summary = "Listar todos os pedidos")
    @GetMapping
    public List<Order> findAll() {
        return repository.findAll();
    }

    @Operation(summary = "Atualizar status do pedido")
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String id,
            @RequestParam OrderStatus status) {
        repository.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Cancelar pedido")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancel(
            @PathVariable String id,
            @RequestParam(defaultValue = "USER_REQUEST") CancellationReason reason) {
        Order cancelled = orderCancellationService.cancel(id, reason);
        return ResponseEntity.ok(cancelled);
    }
}