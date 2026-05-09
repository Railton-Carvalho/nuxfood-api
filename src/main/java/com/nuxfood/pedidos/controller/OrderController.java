package com.nuxfood.pedidos.controller;

import com.nuxfood.pedidos.model.Order;
import com.nuxfood.pedidos.model.PagedResponse;
import com.nuxfood.pedidos.model.enums.OrderStatus;
import com.nuxfood.pedidos.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository repository;

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody Order order) {
        order.setOrderId(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());
        repository.save(order);
        return ResponseEntity.status(201).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> findById(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/paged")
    public PagedResponse<Order> findAll(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String lastKey) {
        return repository.findAllPaged(limit, lastKey);
    }

    @GetMapping("/user/{userId}/status/{status}")
    public List<Order> findAllByUserAndStatus(
            @PathVariable String userId,
            @PathVariable OrderStatus status
    ){
        return repository.findByUserAndStatus(userId, status);
    }

    @GetMapping("/user/{userId}")
    public List<Order> findByUser(@PathVariable String userId) {
        return repository.findByUserId(userId);
    }

    @GetMapping
    public List<Order> findAll() {
        return repository.findAll();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String id,
            @RequestParam OrderStatus status) {
        repository.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }
}