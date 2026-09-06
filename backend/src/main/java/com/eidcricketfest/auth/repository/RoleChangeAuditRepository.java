package com.eidcricketfest.auth.repository;

import com.eidcricketfest.auth.entity.RoleChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit, Long> {}
