-- Extend V119 without changing an already applied migration. Browser identities
-- include the historical unlabelled format and every current journey label.
CREATE OR REPLACE FUNCTION stoxsim_is_controlled_test_identity(candidate_email text, candidate_name text)
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
            AND lower(candidate_email) ~ '^m7-[0-9a-f]{24}@stoxsim\.test$')
        OR (candidate_name = 'Browser Learner'
            AND lower(candidate_email) ~ '^browser-((auth|markets|finwiz|lifecycle|sandbox-switcher|onboarding|progression|competitions|campus-verification|pro-provisioning)-)?[0-9]+-[0-9a-f]+@stoxsim\.test$')
        OR (candidate_name = 'Recovery Drill'
            AND lower(candidate_email) ~ '^recovery-[0-9]+-[0-9]+@stoxsim\.test$')
        OR (candidate_name = 'Synthetic capacity learner'
            AND lower(candidate_email) ~ '^m7-([1-9]|1[0-2])@stoxsim\.test$'), false);
$$;

-- Preserve existing manual markers, privileges and account records.
UPDATE app_user SET is_test_account = true
WHERE NOT is_test_account
  AND stoxsim_is_controlled_test_identity(email, display_name);
