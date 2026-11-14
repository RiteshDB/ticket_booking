package com.ticketing.platform.user.service;

import com.ticketing.platform.security.RefreshTokenCodec;
import com.ticketing.platform.security.RefreshTokenProperties;
import com.ticketing.platform.security.TokenService;
import com.ticketing.platform.user.api.SessionResponse;
import com.ticketing.platform.user.domain.RefreshToken;
import com.ticketing.platform.user.domain.RefreshTokenFamily;
import com.ticketing.platform.user.repository.RefreshTokenFamilyRepository;
import com.ticketing.platform.user.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenFamilyRepository families;
    private final RefreshTokenRepository tokens;
    private final RefreshTokenCodec codec;
    private final RefreshTokenProperties properties;
    private final TokenService accessTokens;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenFamilyRepository families,
                               RefreshTokenRepository tokens, RefreshTokenCodec codec,
                               RefreshTokenProperties properties, TokenService accessTokens, Clock clock) {
        this.families = families;
        this.tokens = tokens;
        this.codec = codec;
        this.properties = properties;
        this.accessTokens = accessTokens;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse startSession(UUID userId) {
        Instant now = now();
        var family = families.saveAndFlush(new RefreshTokenFamily(
                userId, now, now.plus(properties.sessionTtl())));
        return issuePair(family, now);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public SessionResponse refresh(String rawToken) {
        String hash = codec.hash(rawToken);
        UUID familyId = tokens.findFamilyIdByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        var family = families.findByIdForUpdate(familyId)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = now();
        if (!family.isActiveAt(now)) throw new InvalidRefreshTokenException();

        // Read the entity only AFTER acquiring the family lock.
        var oldToken = tokens.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (oldToken.isRevoked()) {
            family.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        if (oldToken.isExpiredAt(now)) throw new InvalidRefreshTokenException();

        oldToken.revoke(now);
        return issuePair(family, now);
    }

    @Transactional
    public void logout(String rawToken) {
        var familyId = tokens.findFamilyIdByTokenHash(codec.hash(rawToken));
        if (familyId.isEmpty()) return;
        families.findByIdForUpdate(familyId.get()).ifPresent(family -> family.revoke(now()));
    }

    private SessionResponse issuePair(RefreshTokenFamily family, Instant now) {
        String raw = codec.generate();
        tokens.saveAndFlush(new RefreshToken(family.getUserId(), family.getId(),
                codec.hash(raw), now, family.getExpiresAt()));
        return SessionResponse.of(accessTokens.issueAccessToken(family.getUserId()),
                raw, family.getExpiresAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}

