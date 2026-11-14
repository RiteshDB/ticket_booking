package com.ticketing.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    private final JwtProperties properties = new JwtProperties(
            "dGVzdC1vbmx5LXNpZ25pbmcta2V5LTAxMjM0NTY3ODk=",
            "https://ticketing.local", "ticketing-api", Duration.ofMinutes(15));
    private final JwtConfig config = new JwtConfig();
    private final javax.crypto.SecretKey key = config.jwtSigningKey(properties);
    private final org.springframework.security.oauth2.jwt.JwtEncoder encoder =
            config.jwtEncoder(key);
    private final org.springframework.security.oauth2.jwt.JwtDecoder decoder =
            config.jwtDecoder(key, properties);

    @Test
    void issuedTokenRoundTripsAndContainsExpectedClaims() {
        UUID id = UUID.randomUUID();
        var response = new TokenService(encoder, properties, Clock.systemUTC())
                .issueAccessToken(id);
        var jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(jwt.getAudience()).contains(properties.audience());
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        byte[] otherBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(otherBytes);
        var other = new JwtProperties(Base64.getEncoder().encodeToString(otherBytes),
                properties.issuer(), properties.audience(), properties.accessTokenTtl());
        var otherEncoder = config.jwtEncoder(config.jwtSigningKey(other));
        String token = new TokenService(otherEncoder, properties, Clock.systemUTC())
                .issueAccessToken(UUID.randomUUID()).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Clock past = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-30));
        String token = new TokenService(encoder, properties, past)
                .issueAccessToken(UUID.randomUUID()).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        var other = new JwtProperties(properties.secret(), "https://another-issuer.local",
                properties.audience(), properties.accessTokenTtl());
        String token = new TokenService(encoder, other, Clock.systemUTC())
                .issueAccessToken(UUID.randomUUID()).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() {
        var other = new JwtProperties(properties.secret(), properties.issuer(),
                "another-api", properties.accessTokenTtl());
        String token = new TokenService(encoder, other, Clock.systemUTC())
                .issueAccessToken(UUID.randomUUID()).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsMissingExpiry() {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(java.util.List.of(properties.audience()))
                .subject(UUID.randomUUID().toString()).issuedAt(java.time.Instant.now()).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader
                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        String token = encoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsNonUuidSubject() {
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(java.util.List.of(properties.audience()))
                .subject("not-a-user-id").issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(900)).build();
        var header = org.springframework.security.oauth2.jwt.JwsHeader
                .with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        String token = encoder.encode(
                        org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
