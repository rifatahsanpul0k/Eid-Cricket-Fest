package com.eidcricketfest.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "role_change_audits")
public class RoleChangeAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "target_user_id", nullable = false) private Long targetUserId;
    @Column(name = "changed_by_user_id", nullable = false) private Long changedByUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RoleCode role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Operation operation;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RoleChangeAudit() {}
    public RoleChangeAudit(Long targetUserId, Long changedByUserId, RoleCode role, Operation operation) {
        this.targetUserId = targetUserId; this.changedByUserId = changedByUserId;
        this.role = role; this.operation = operation; this.createdAt = Instant.now();
    }
    public enum Operation { GRANT, REVOKE }
}
