package com.eidcricketfest.auth.dto;

import com.eidcricketfest.auth.entity.RoleCode;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserRolesRequest(@NotNull Set<RoleCode> roles) {}
