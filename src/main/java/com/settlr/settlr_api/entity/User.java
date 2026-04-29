package com.settlr.settlr_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an application user.
 * Table name "users" avoids the PostgreSQL reserved word "user".
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    /** Groups this user belongs to (inverse side of the join table). */
    @ManyToMany(mappedBy = "members", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Group> groups = new ArrayList<>();
}
