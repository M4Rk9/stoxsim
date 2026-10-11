-- Reserved operational identities, not a blanket exclusion for names/domains
-- containing "test". Public registration/profile DTOs cannot set this marker.
ALTER TABLE app_user ADD COLUMN is_test_account boolean NOT NULL DEFAULT false;

CREATE TABLE controlled_test_mailbox (
    base_email varchar(320) PRIMARY KEY,
    CHECK (base_email = lower(base_email) AND base_email NOT LIKE '%+%')
);
INSERT INTO controlled_test_mailbox(base_email) VALUES ('support.stoxsim@gmail.com');

CREATE FUNCTION stoxsim_is_controlled_test_identity(candidate_email text, candidate_name text)
RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        (candidate_name = 'Production Smoke' AND EXISTS (
            SELECT 1 FROM controlled_test_mailbox m
            WHERE split_part(lower(candidate_email), '@', 2) = split_part(m.base_email, '@', 2)
              AND split_part(lower(candidate_email), '+', 1) = split_part(m.base_email, '@', 1)
              AND lower(candidate_email) ~ '^[^+@]+\+production-smoke-[0-9]+-[0-9]+@[^@]+$'
        ))
        OR (candidate_name = 'Authorization A'
            AND lower(candidate_email) ~ '^authorization-a-[0-9]+-[0-9]+-[0-9]+@stoxsim\.test$')
        OR (candidate_name = 'Authorization B'
            AND lower(candidate_email) ~ '^authorization-b-[0-9]+-[0-9]+-[0-9]+@stoxsim\.test$')
        OR (candidate_name = 'Staging Smoke'
            AND lower(candidate_email) ~ '^staging-smoke-[0-9]+-[0-9]+@stoxsim\.test$')
        OR (candidate_name = 'StoxSim Load Test'
            AND lower(candidate_email) ~ '^load-[0-9]+-[0-9]+@stoxsim\.test$')
        OR (candidate_name = 'CI capacity fixture'
            AND lower(candidate_email) ~ '^m7-[0-9a-f]{24}@stoxsim\.test$'), false);
$$;

-- Classify before INSERT commits, so even a lost registration response or failed
-- cleanup cannot briefly inflate the dashboard. The marker survives later profile
-- changes because it is not mapped to a client-editable entity/DTO field.
CREATE FUNCTION stoxsim_mark_controlled_test_account()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.is_test_account := NEW.is_test_account
        OR stoxsim_is_controlled_test_identity(NEW.email, NEW.display_name);
    RETURN NEW;
END;
$$;
CREATE TRIGGER mark_controlled_test_account BEFORE INSERT ON app_user
FOR EACH ROW EXECUTE FUNCTION stoxsim_mark_controlled_test_account();

-- Mark narrowly identified historical leftovers; keep their records for review.
-- This migration does not delete accounts or change privileges/subscriptions.
UPDATE app_user SET is_test_account = true
WHERE stoxsim_is_controlled_test_identity(email, display_name);
