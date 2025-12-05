package com.shop.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "order_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // тип агрегата, на будущее (например, ORDER)
    @Column(nullable = false)
    private String aggregateType;

    // id заказа
    @Column(nullable = false)
    private Long aggregateId;

    // тип события, например ORDER_CREATED
    @Column(nullable = false)
    private String type;

    // JSON события
    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}
