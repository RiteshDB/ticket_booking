package com.ticketing.platform.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

    @Test
    void normalizesEmailToTrimmedLowercase() {
        User user = new User("  Ritesh@Example.COM ", "hash", " Ritesh ", Role.USER);

        assertThat(user.getEmail()).isEqualTo("ritesh@example.com");
        assertThat(user.getFullName()).isEqualTo("Ritesh");
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new User("a@b.com", null, "Name", Role.USER));
    }

    @Test
    void usersWithoutIdAreNotEqual() {
        User a = new User("a@b.com", "hash", "A", Role.USER);
        User b = new User("a@b.com", "hash", "A", Role.USER);

        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(a);
    }
}