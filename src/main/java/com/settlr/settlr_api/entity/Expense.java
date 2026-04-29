package com.settlr.settlr_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an expense logged in a group.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The group this expense belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    /** The user who paid for this expense. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Column(nullable = false)
    private String description;

    /** Total amount paid — must be BigDecimal for precision. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** ISO 4217 currency code, e.g. "INR", "USD". */
    @Column(nullable = false, length = 3)
    private String currency;

    /** How the expense is split among participants. */
    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExpenseSplit> splits = new ArrayList<>();
}
