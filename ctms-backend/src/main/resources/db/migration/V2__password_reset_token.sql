-- Backs the forgot/reset-password flow (Phase 0).
CREATE TABLE password_reset_token (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_token_user ON password_reset_token(user_id);
