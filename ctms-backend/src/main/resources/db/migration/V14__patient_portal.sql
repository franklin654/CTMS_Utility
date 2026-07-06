ALTER TABLE subject ADD COLUMN linked_user_id BIGINT UNIQUE REFERENCES users(id);
