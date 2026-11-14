CREATE TABLE refresh_token_families (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_refresh_family_user UNIQUE (id, user_id)
);

ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;

-- Preserve any pre-existing rows: each becomes a separate legacy session.
INSERT INTO refresh_token_families (id, user_id, created_at, expires_at, revoked_at)
SELECT id, user_id, created_at, expires_at, revoked_at FROM refresh_tokens;

UPDATE refresh_tokens SET family_id = id;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_token_family
    FOREIGN KEY (family_id, user_id)
    REFERENCES refresh_token_families (id, user_id) ON DELETE CASCADE;

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_families_user_active
    ON refresh_token_families (user_id) WHERE revoked_at IS NULL;

