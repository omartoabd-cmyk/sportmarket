-- Preserve V1 and its unique constraint. Existing case/space collisions must
-- be resolved explicitly before migration; do not silently merge accounts.
UPDATE users SET email = LOWER(TRIM(email));

ALTER TABLE users ADD CONSTRAINT ck_users_email_normalized
    CHECK (email = LOWER(TRIM(email)));
