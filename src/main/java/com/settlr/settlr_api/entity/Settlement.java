package com.settlr.settlr_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records an actual payment from one user to another within a group.
 * This is the "I paid you back" action — as opposed to Expense which is
 * "I paid for something we share".
 */
@Entity
@Table(name = "settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The group in which this settlement takes place. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /** The user who is paying (the debtor). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    /** The user who is receiving the payment (the creditor). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    /** Amount paid — always positive, always BigDecimal. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Settlement lifecycle: PENDING → CONFIRMED / REJECTED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'PENDING'")
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    /** Timestamp when the recipient confirmed/rejected. Null while PENDING. */
    private java.time.Instant resolvedDate;
}
