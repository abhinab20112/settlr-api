package com.settlr.settlr_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tracks the net balance between two users within a group.
 * Positive balance  → fromUser owes toUser.
 * Negative balance  → toUser owes fromUser.
 *
 * @Version ensures optimistic locking to prevent concurrent write conflicts
 * when multiple expense splits are processed simultaneously.
 */
@Entity
@Table(
        name = "user_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_balance_from_to_group",
                columnNames = {"from_user_id", "to_user_id", "group_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBalance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The user who owes. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    /** The user who is owed. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    /** The group context for this balance. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /** Net amount fromUser owes toUser. Must be BigDecimal for precision. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    /**
     * Optimistic locking version — only on UserBalance as it is the most
     * concurrently updated entity (every expense settlement touches it).
     */
    @Version
    private Long version;
}
