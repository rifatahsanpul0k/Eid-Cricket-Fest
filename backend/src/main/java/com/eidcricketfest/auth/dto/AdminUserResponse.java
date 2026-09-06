package com.eidcricketfest.auth.dto;

import com.eidcricketfest.auth.entity.RoleCode;
import java.util.Set;

public record AdminUserResponse(Long userId, String displayName, String email, Set<RoleCode> roles) {}
