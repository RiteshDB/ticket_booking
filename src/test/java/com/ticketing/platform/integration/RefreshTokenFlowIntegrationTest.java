package com.ticketing.platform.integration;

import com.ticketing.platform.TestcontainersConfiguration;
import com.ticketing.platform.security.RefreshTokenCodec;
import com.ticketing.platform.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RefreshTokenCodec codec;
    @MockitoSpyBean TokenService accessTokens;

    private UUID userId;
    private String email;

    @AfterEach
    void removeTestUser() {
        if (userId != null) jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void loginStoresOnlyRefreshHash() throws Exception {
        String login = registerAndLogin();
        String raw = JsonPath.read(login, "$.refreshToken");
        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, userId);
        assertThat(stored).isEqualTo(codec.hash(raw)).isNotEqualTo(raw).hasSize(64);
    }

    @Test
    void rotationAndReplayRevokeTheWholeFamilyPermanently() throws Exception {
        String login = registerAndLogin();
        String original = JsonPath.read(login, "$.refreshToken");
        var rotation = refresh(original);
        assertThat(rotation.getResponse().getStatus()).isEqualTo(200);
        String rotated = rotation.getResponse().getContentAsString();
        String replacement = JsonPath.read(rotated, "$.refreshToken");
        assertThat(replacement).isNotEqualTo(original);
        assertThat((String) JsonPath.read(rotated, "$.refreshExpiresAt"))
                .isEqualTo(JsonPath.read(login, "$.refreshExpiresAt"));
        assertThat(refresh(original).getResponse().getStatus()).isEqualTo(401);
        // This separate request proves the revocation survived the failed replay request.
        assertThat(refresh(replacement).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM refresh_token_families WHERE user_id = ?",
                Boolean.class, userId)).isTrue();
    }

    @Test
    void logoutIsIdempotentButDoesNotRevokeExistingAccessJwt() throws Exception {
        String login = registerAndLogin();
        String raw = JsonPath.read(login, "$.refreshToken");
        String access = JsonPath.read(login, "$.accessToken");
        logout(raw).andExpect(status().isNoContent());
        logout(raw).andExpect(status().isNoContent());
        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void unknownRefreshFailsButUnknownLogoutSucceeds() throws Exception {
        String unknown = codec.generate();
        assertThat(refresh(unknown).getResponse().getStatus()).isEqualTo(401);
        logout(unknown).andExpect(status().isNoContent());
    }

    @Test
    void expiredFamilyCannotRefresh() throws Exception {
        String login = registerAndLogin();
        jdbc.update("UPDATE refresh_token_families SET expires_at = now() - interval '1 minute' "
                + "WHERE user_id = ?", userId);
        assertThat(refresh(JsonPath.read(login, "$.refreshToken"))
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logoutAffectsOnlyOneLoginFamily() throws Exception {
        String first = registerAndLogin();
        String second = login();
        logout(JsonPath.read(first, "$.refreshToken")).andExpect(status().isNoContent());
        assertThat(refresh(JsonPath.read(second, "$.refreshToken"))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentRefreshAllowsOneRotationThenDetectsReuse() throws Exception {
        String login = registerAndLogin();
        String raw = JsonPath.read(login, "$.refreshToken");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<MvcResult> attempt = () -> {
                start.await();
                return refresh(raw);
            };
            var a = pool.submit(attempt);
            var b = pool.submit(attempt);
            start.countDown();
            var first = a.get(15, TimeUnit.SECONDS);
            var second = b.get(15, TimeUnit.SECONDS);
            assertThat(java.util.List.of(first.getResponse().getStatus(),
                    second.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
            var winner = first.getResponse().getStatus() == 200 ? first : second;
            String latest = JsonPath.read(winner.getResponse().getContentAsString(), "$.refreshToken");
            assertThat(refresh(latest).getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void issuanceFailureRollsBackRotationAndOriginalRemainsUsable() throws Exception {
        String login = registerAndLogin();
        String raw = JsonPath.read(login, "$.refreshToken");
        doThrow(new IllegalStateException("simulated signing failure"))
                .when(accessTokens).issueAccessToken(any());
        assertThatThrownBy(() -> refresh(raw))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        doCallRealMethod().when(accessTokens).issueAccessToken(any());
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, codec.hash(raw))).isTrue();
        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentLogoutAndRefreshCannotLeaveAUsableRefreshToken() throws Exception {
        String login = registerAndLogin();
        String raw = JsonPath.read(login, "$.refreshToken");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var rotation = pool.submit(() -> {
                start.await();
                return refresh(raw);
            });
            var revocation = pool.submit(() -> {
                start.await();
                return logout(raw).andReturn();
            });
            start.countDown();
            var result = rotation.get(15, TimeUnit.SECONDS);
            assertThat(revocation.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(204);
            assertThat(result.getResponse().getStatus()).isIn(200, 401);
            if (result.getResponse().getStatus() == 200) {
                String latest = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
                assertThat(refresh(latest).getResponse().getStatus()).isEqualTo(401);
            }
            assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(401);
        }
    }

    private String registerAndLogin() throws Exception {
        email = "refresh-" + UUID.randomUUID() + "@example.com";
        String registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        userId = UUID.fromString(JsonPath.read(registration, "$.id"));
        return login();
    }

    private String login() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String credentials() {
        return """
                {"email":"%s","password":"a-long-demo-password","fullName":"Refresh Demo"}
                """.formatted(email);
    }

    private MvcResult refresh(String raw) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody(raw)))
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions logout(String raw) throws Exception {
        return mvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON).content(tokenBody(raw)));
    }

    private String tokenBody(String raw) {
        return "{\"refreshToken\":\"" + raw + "\"}";
    }
}
