-- A null hash means no local password exists; existing passwords are preserved.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;
