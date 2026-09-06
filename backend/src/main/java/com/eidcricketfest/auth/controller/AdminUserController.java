package com.eidcricketfest.auth.controller;

import com.eidcricketfest.auth.dto.*;
import com.eidcricketfest.auth.service.AdminUserService;
import com.eidcricketfest.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Users")
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Search users for role management")
    public PageResponse<AdminUserResponse> search(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "20") Integer size, @RequestParam(defaultValue = "displayName") String sortBy, @RequestParam(defaultValue = "asc") String direction) {
        return service.search(q, page, size, sortBy, direction);
    }
    @GetMapping("/{userId}") @Operation(summary = "Get a user for role management") public AdminUserResponse get(@PathVariable Long userId) { return service.get(userId); }
    @PatchMapping("/{userId}/roles")
    @Operation(summary = "Update a user's operational roles")
    public AdminUserResponse updateRoles(@PathVariable Long userId, @Valid @RequestBody UpdateUserRolesRequest request, @AuthenticationPrincipal Jwt jwt) {
        return service.updateRoles(userId, request.roles(), Long.valueOf(jwt.getSubject()));
    }
}
