package com.eidcricketfest.auth.service;

import com.eidcricketfest.auth.dto.AdminUserResponse;
import com.eidcricketfest.auth.entity.*;
import com.eidcricketfest.auth.repository.*;
import com.eidcricketfest.common.dto.PageResponse;
import com.eidcricketfest.common.exception.ResourceNotFoundException;
import com.eidcricketfest.common.web.PageableFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AdminUserService {
    private static final Set<RoleCode> MUTABLE = EnumSet.of(RoleCode.SCORER, RoleCode.ORGANIZER);
    private static final Map<String, String> SORTS = Map.of("displayName", "displayName", "email", "email", "id", "id");
    private final UserRepository users;
    private final RoleRepository roles;
    private final RoleChangeAuditRepository audits;
    private final PageableFactory pages;

    public AdminUserService(UserRepository users, RoleRepository roles, RoleChangeAuditRepository audits, PageableFactory pages) {
        this.users = users; this.roles = roles; this.audits = audits; this.pages = pages;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String query, Integer page, Integer size, String sortBy, String direction) {
        Pageable pageable = pages.create(page, size, sortBy, direction, SORTS, "displayName");
        return PageResponse.from(users.searchForAdministration(query == null ? "" : query.trim(), pageable).map(this::response));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long userId) { return response(requireUser(userId)); }

    @Transactional
    public AdminUserResponse updateRoles(Long userId, Set<RoleCode> requested, Long actorId) {
        if (!MUTABLE.containsAll(requested)) throw new IllegalArgumentException("Only SCORER and ORGANIZER roles can be managed");
        User user = requireUser(userId);
        Set<RoleCode> current = roleCodes(user);
        for (RoleCode code : MUTABLE) {
            boolean has = current.contains(code), wants = requested.contains(code);
            if (has == wants) continue;
            Role role = roles.findByCode(code).orElseThrow(() -> new IllegalStateException(code + " role is missing"));
            if (wants) user.addRole(role); else user.removeRole(role);
            audits.save(new RoleChangeAudit(userId, actorId, code, wants ? RoleChangeAudit.Operation.GRANT : RoleChangeAudit.Operation.REVOKE));
        }
        return response(users.save(user));
    }

    private User requireUser(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    private AdminUserResponse response(User user) { return new AdminUserResponse(user.getId(), user.getDisplayName(), user.getEmail(), roleCodes(user)); }
    private Set<RoleCode> roleCodes(User user) { Set<RoleCode> result = EnumSet.noneOf(RoleCode.class); user.getRoles().forEach(role -> result.add(role.getCode())); return result; }
}
