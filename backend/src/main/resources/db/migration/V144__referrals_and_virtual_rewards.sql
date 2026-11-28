ALTER TABLE virtual_account
    DROP CONSTRAINT ck_virtual_account_scope,
    DROP CONSTRAINT ck_virtual_account_starting_capital,
    ADD CONSTRAINT ck_virtual_account_scope CHECK (
        (account_kind = 'STANDARD' AND sandbox_plan IS NULL AND sandbox_slot = 0 AND leaderboard_eligible)
        OR (account_kind = 'SANDBOX' AND sandbox_plan IN ('PLUS', 'PRO') AND sandbox_slot BETWEEN 1 AND 5 AND NOT leaderboard_eligible)
        OR (account_kind = 'REWARD' AND sandbox_plan IS NULL AND sandbox_slot = 0 AND NOT leaderboard_eligible
            AND ((market_region = 'INDIA' AND currency = 'INR') OR (market_region = 'UNITED_STATES' AND currency = 'USD')))),
    ADD CONSTRAINT ck_virtual_account_starting_capital CHECK (
        starting_capital > 0 OR (account_kind = 'REWARD' AND starting_capital = 0));
CREATE UNIQUE INDEX uk_virtual_account_reward_region
    ON virtual_account(user_id, market_region) WHERE account_kind = 'REWARD';

CREATE TABLE referral_code (
    code varchar(24) PRIMARY KEY CHECK (code ~ '^[A-Z2-7]{16}$'),
    referrer_id uuid NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE TABLE referral_attribution (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
    referred_id uuid UNIQUE REFERENCES app_user(id) ON DELETE SET NULL,
    status varchar(12) NOT NULL CHECK (status IN ('PENDING', 'QUALIFIED', 'REWARDED', 'REJECTED')),
    rejection varchar(24) CHECK (rejection IN ('DUPLICATE_IDENTITY', 'ACCOUNT_DELETED', 'EXPIRED', 'ADMIN_REJECTED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    qualified_at timestamptz,
    reviewed_at timestamptz,
    reviewer_id uuid REFERENCES app_user(id) ON DELETE SET NULL,
    CHECK (referrer_id IS DISTINCT FROM referred_id OR referrer_id IS NULL),
    CHECK ((status = 'REJECTED') = (rejection IS NOT NULL))
);
CREATE INDEX idx_referral_referrer_recent ON referral_attribution(referrer_id,created_at DESC);
CREATE INDEX idx_referral_pending ON referral_attribution(created_at,id) WHERE status = 'PENDING';
CREATE INDEX idx_referral_review ON referral_attribution(qualified_at,id) WHERE status = 'QUALIFIED';

CREATE FUNCTION canonical_referral_email(address text) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT CASE WHEN split_part(lower(btrim(address)), '@', 2) IN ('gmail.com', 'googlemail.com')
        THEN replace(split_part(split_part(lower(btrim(address)), '@', 1), '+', 1), '.', '') || '@gmail.com'
        ELSE lower(btrim(address)) END
$$;
CREATE INDEX idx_verified_referral_address
    ON app_user(canonical_referral_email(email)) WHERE email_verified_at IS NOT NULL;

CREATE TABLE referral_identity_hold (
    identity_hash char(64) PRIMARY KEY CHECK (identity_hash ~ '^[a-f0-9]{64}$'),
    expires_at timestamptz NOT NULL
);
CREATE INDEX idx_referral_identity_expiry ON referral_identity_hold(expires_at);

CREATE TABLE virtual_reward_transaction (
    id uuid PRIMARY KEY,
    source varchar(24) NOT NULL CHECK (source IN ('REFERRAL', 'REWARDED_AD', 'ADMIN_CORRECTION')),
    source_id uuid NOT NULL,
    actor_id uuid,
    reverses_id uuid UNIQUE REFERENCES virtual_reward_transaction(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source,source_id),
    CHECK ((source = 'ADMIN_CORRECTION') = (reverses_id IS NOT NULL)),
    CHECK (source = 'REWARDED_AD' OR actor_id IS NOT NULL)
);

-- Portfolio IDs remain as opaque audit references if the owning account is deleted.
CREATE TABLE virtual_reward_entry (
    transaction_id uuid NOT NULL REFERENCES virtual_reward_transaction(id),
    currency varchar(3) NOT NULL CHECK (currency IN ('INR', 'USD')),
    account_id uuid NOT NULL,
    amount numeric(19,4) NOT NULL CHECK (amount <> 0),
    PRIMARY KEY (transaction_id,currency)
);
CREATE INDEX idx_virtual_reward_entry_account ON virtual_reward_entry(account_id,transaction_id);

CREATE FUNCTION protect_virtual_rewards() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Virtual reward records are append-only';
END;
$$;
CREATE TRIGGER virtual_reward_transaction_immutable BEFORE UPDATE OR DELETE ON virtual_reward_transaction
    FOR EACH ROW EXECUTE FUNCTION protect_virtual_rewards();
CREATE TRIGGER virtual_reward_entry_immutable BEFORE UPDATE OR DELETE ON virtual_reward_entry
    FOR EACH ROW EXECUTE FUNCTION protect_virtual_rewards();

-- Check the complete pair at commit, after both accounts and entries have been written.
CREATE FUNCTION require_virtual_reward_pair() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    reward_id uuid := NEW.id;
BEGIN
    IF (SELECT count(*) FROM virtual_reward_entry WHERE transaction_id = reward_id) <> 2 THEN
        RAISE EXCEPTION 'Virtual reward must have one entry in each currency';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER virtual_reward_pair AFTER INSERT ON virtual_reward_transaction
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_virtual_reward_pair();

CREATE FUNCTION reject_incomplete_referrals_on_deletion() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status IN ('PENDING', 'QUALIFIED')
        AND (NEW.referrer_id IS NULL OR NEW.referred_id IS NULL) THEN
        NEW.status := 'REJECTED';
        NEW.rejection := 'ACCOUNT_DELETED';
        NEW.reviewed_at := now();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER referral_on_user_deletion BEFORE UPDATE OF referrer_id, referred_id ON referral_attribution
    FOR EACH ROW EXECUTE FUNCTION reject_incomplete_referrals_on_deletion();
