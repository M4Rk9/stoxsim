ALTER TABLE virtual_account
    ADD COLUMN provisioning_key VARCHAR(100),
    ADD CONSTRAINT ck_virtual_account_provisioning_scope CHECK (
        account_kind = 'SANDBOX' OR provisioning_key IS NULL
    );

DROP INDEX uk_virtual_account_user_region_scope_slot;

CREATE UNIQUE INDEX uk_virtual_account_standard_region
    ON virtual_account(user_id, market_region)
    WHERE account_kind = 'STANDARD';

CREATE UNIQUE INDEX uk_virtual_account_sandbox_plan_slot
    ON virtual_account(user_id, market_region, sandbox_plan, sandbox_slot)
    WHERE account_kind = 'SANDBOX';

CREATE UNIQUE INDEX uk_virtual_account_provisioning_key
    ON virtual_account(user_id, provisioning_key)
    WHERE provisioning_key IS NOT NULL;
