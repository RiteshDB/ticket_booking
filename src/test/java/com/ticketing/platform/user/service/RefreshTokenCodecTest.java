package com.ticketing.platform.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefreshTokenCodecTest {
    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void generatesUrlSafeTokensWithExpectedLength() {
        assertThat(codec.generate()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void independentlyGeneratedTokensDiffer() {
        assertThat(codec.generate()).isNotEqualTo(codec.generate());
    }

    @Test
    void hashesDeterministicallyWithoutStoringRawValue() {
        String raw = codec.generate();
        assertThat(codec.hash(raw)).matches("[0-9a-f]{64}")
                .isNotEqualTo(raw).isEqualTo(codec.hash(raw));
        assertThat(codec.hash(raw)).isNotEqualTo(codec.hash(codec.generate()));
    }
}
