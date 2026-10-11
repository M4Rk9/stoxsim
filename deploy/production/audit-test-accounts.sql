-- Runs before or after V119. to_jsonb handles the pre-migration absent marker.
BEGIN READ ONLY;
WITH classified AS (
    SELECT u.*,
        COALESCE((to_jsonb(u)->>'is_test_account')::boolean, false) AS marked_test,
        (
            (display_name = 'Production Smoke'
             AND split_part(lower(email), '@', 2) = split_part(lower(:'smoke_base_email'), '@', 2)
             AND split_part(lower(email), '+', 1) = split_part(lower(:'smoke_base_email'), '@', 1)
             AND lower(email) ~ '^[^+@]+\+production-smoke-[0-9]+-[0-9]+@[^@]+$')
            OR (display_name = 'Authorization A' AND lower(email) ~ '^authorization-a-[0-9]+-[0-9]+-[0-9]+@stoxsim\.test$')
            OR (display_name = 'Authorization B' AND lower(email) ~ '^authorization-b-[0-9]+-[0-9]+-[0-9]+@stoxsim\.test$')
            OR (display_name = 'Staging Smoke' AND lower(email) ~ '^staging-smoke-[0-9]+-[0-9]+@stoxsim\.test$')
            OR (display_name = 'StoxSim Load Test' AND lower(email) ~ '^load-[0-9]+-[0-9]+@stoxsim\.test$')
            OR (display_name = 'CI capacity fixture' AND lower(email) ~ '^m7-[0-9a-f]{24}@stoxsim\.test$')
        ) AS reserved_identity
    FROM app_user u
)
SELECT COUNT(*) FILTER (WHERE platform_role = 'USER') AS stored_learner_accounts,
       COUNT(*) FILTER (WHERE platform_role = 'USER' AND marked_test) AS marked_test_learners,
       COUNT(*) FILTER (WHERE platform_role = 'USER' AND reserved_identity AND NOT marked_test) AS unmarked_reserved_test_learners,
       COUNT(*) FILTER (WHERE platform_role = 'USER' AND NOT marked_test AND NOT reserved_identity) AS learners_outside_reserved_tests
FROM classified;

\if :show_details
-- Names alone are candidates for manual review, never automatic deletion.
SELECT id, email, display_name, platform_role, email_verified_at, created_at,
       COALESCE((to_jsonb(u)->>'is_test_account')::boolean, false) AS marked_test
FROM app_user u
WHERE COALESCE((to_jsonb(u)->>'is_test_account')::boolean, false)
   OR display_name IN ('Production Smoke', 'Authorization A', 'Authorization B',
                       'Staging Smoke', 'StoxSim Load Test', 'CI capacity fixture')
ORDER BY created_at DESC LIMIT 100;
\endif
ROLLBACK;
