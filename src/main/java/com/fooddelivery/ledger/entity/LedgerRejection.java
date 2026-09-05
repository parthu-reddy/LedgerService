package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.OffsetDateTime;

import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ledger_rejections", 
       indexes = {
           @Index(name = "idx_rejections_resolved_at", columnList = "resolved_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerRejection {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "producer", length = 64)
    private String producer;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;
}
