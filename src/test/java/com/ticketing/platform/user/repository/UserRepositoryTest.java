package com.ticketing.platform.user.repository;

import com.ticketing.platform.TestcontainersConfiguration;
import com.ticketing.platform.config.JpaAuditingConfig;
import com.ticketing.platform.user.domain.Role;
import com.ticketing.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesUserAndFillsIdAuditFieldsAndVersion() {
        User saved = userRepository.saveAndFlush(newUser("ritesh@example.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void findsUserByEmail() {
        userRepository.saveAndFlush(newUser("ritesh@example.com"));
        entityManager.clear(); // force a real SELECT instead of reading the cache

        assertThat(userRepository.findByEmail("ritesh@example.com"))
                .get()
                .extracting(User::getFullName, User::getRole)
                .containsExactly("Ritesh", Role.USER);
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmailAtDatabaseLevel() {
        userRepository.saveAndFlush(newUser("ritesh@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("RITESH@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incrementsVersionOnUpdate() {
        User saved = userRepository.saveAndFlush(newUser("ritesh@example.com"));

        saved.changeRole(Role.ORGANIZER);
        User updated = userRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1);
    }

    private static User newUser(String email) {
        return new User(email, "{bcrypt}dummy-hash", "Ritesh", Role.USER);
    }
}