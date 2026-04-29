package com.settlr.settlr_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Shared audit fields inherited by every entity.
 * Requires @EnableJpaAuditing (see JpaAuditingConfig).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdDate;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant lastModifiedDate;
}
