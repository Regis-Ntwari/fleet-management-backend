package com.limoz.fleet.auth;

import com.limoz.fleet.auth.dto.LoginRequest;
import com.limoz.fleet.auth.dto.RefreshRequest;
import com.limoz.fleet.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("login returns access + refresh tokens and the user profile with roles")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.roles", hasItem("SUPER_ADMIN")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("wrong password is rejected with 401 and a consistent error body")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest(ADMIN_EMAIL, "nope-nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    @DisplayName("protected endpoints require a bearer token")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    @DisplayName("refresh rotates the token pair and the old refresh token cannot be reused")
    void refreshRotates() throws Exception {
        var first = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("repeated failed logins lock the account and throttle the client")
    void lockoutAfterRepeatedFailures() throws Exception {
        String email = "locked.user@test.limoz.rw";
        userService.create(new com.limoz.fleet.user.dto.CreateUserRequest("Locked", "User", email, null, null,
                "Correct@12345", java.util.Set.of("VIEWER"), null, false));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(new LoginRequest(email, "wrong-" + i))))
                    .andExpect(status().isUnauthorized());
        }
        // 5 failures (settings security.max_failed_logins) -> account locked even with the right password
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest(email, "Correct@12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        // administrator reset clears the lock
        Long id = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        userService.resetPassword(id, new com.limoz.fleet.user.dto.ResetPasswordRequest("Correct@12345", false));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(new LoginRequest(email, "Correct@12345"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout revokes the access token immediately")
    void logoutRevokesAccessToken() throws Exception {
        var session = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String bearer = "Bearer " + session.accessToken();
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer))
                .andExpect(status().isUnauthorized());
    }
}
