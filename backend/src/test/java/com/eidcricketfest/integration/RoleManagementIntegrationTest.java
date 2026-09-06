package com.eidcricketfest.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleManagementIntegrationTest extends AuthTestSupport {

    @Test
    void adminCanSearchGrantAndRevokeRolesWithAuditAndReloginRefresh() throws Exception {
        TestTokens admin = promoted("ADMIN");
        TestTokens player = registerPlayer();
        long userId = userId(player.email());

        mockMvc.perform(get("/api/v1/admin/users").param("q", player.email()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].userId").value(userId))
                .andExpect(jsonPath("$.content[0].roles[0]").value("PLAYER"));

        update(admin, userId, new String[]{"SCORER", "ORGANIZER"})
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(3));

        assertThat(auditCount(userId, "GRANT")).isEqualTo(2);
        assertThat(loginJson(player).path("user").path("roles").toString()).contains("SCORER", "ORGANIZER");

        update(admin, userId, new String[]{})
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("PLAYER"));
        assertThat(auditCount(userId, "REVOKE")).isEqualTo(2);
        assertThat(loginJson(player).path("user").path("roles").toString()).doesNotContain("SCORER", "ORGANIZER");
    }

    @Test
    void onlyAdminCanManageSupportedRolesAndRegistrationStaysPlayerOnly() throws Exception {
        TestTokens player = registerPlayer();
        long userId = userId(player.email());
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(player))).andExpect(status().isForbidden());

        for (String role : new String[]{"SCORER", "ORGANIZER"}) {
            TestTokens privileged = promoted(role);
            mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(privileged))).andExpect(status().isForbidden());
        }

        TestTokens admin = promoted("ADMIN");
        update(admin, userId, new String[]{"ADMIN"}).andExpect(status().isBadRequest());
        assertThat(loginJson(player).path("user").path("roles").toString()).isEqualTo("[\"PLAYER\"]");
    }

    private TestTokens promoted(String role) throws Exception { TestTokens tokens = registerPlayer(); addRole(tokens.email(), role); return login(tokens.email(), tokens.password()); }
    private long userId(String email) { return jdbcTemplate.queryForObject("SELECT id FROM users WHERE LOWER(email)=LOWER(?)", Long.class, email); }
    private long auditCount(long userId, String operation) { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_change_audits WHERE target_user_id=? AND operation=?", Long.class, userId, operation); }
    private org.springframework.test.web.servlet.ResultActions update(TestTokens admin, long userId, String[] roles) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/{id}/roles", userId).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("roles", roles))));
    }
    private tools.jackson.databind.JsonNode loginJson(TestTokens user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(Map.of("identifier", user.email(), "password", user.password())))).andExpect(status().isOk()).andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
    private String bearer(TestTokens tokens) { return "Bearer " + tokens.accessToken(); }
}
