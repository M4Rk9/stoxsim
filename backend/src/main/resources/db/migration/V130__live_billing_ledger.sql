-- Additive, disabled-by-default billing. Provider references never contain card data.
CREATE TABLE billing_customer(user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,account_reference UUID NOT NULL UNIQUE);
CREATE TABLE billing_purchase(
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 provider VARCHAR(16) NOT NULL CHECK(provider IN ('RAZORPAY','GOOGLE_PLAY')),
 plan VARCHAR(8) NOT NULL CHECK(plan IN ('PLUS','PRO')),request_key UUID NOT NULL,
 provider_reference VARCHAR(160),provider_product VARCHAR(160) NOT NULL,token_hash VARCHAR(64) UNIQUE,encrypted_token TEXT,
 status VARCHAR(32) NOT NULL DEFAULT 'CREATING',paid_through TIMESTAMPTZ,access_until TIMESTAMPTZ,
 verified_paid_count INTEGER NOT NULL DEFAULT 0,cancellation_status VARCHAR(16) NOT NULL DEFAULT 'NONE' CHECK(cancellation_status IN ('NONE','REQUESTED','CONFIRMED')),
 cancel_at TIMESTAMPTZ,remaining_cycles INTEGER,revoked BOOLEAN NOT NULL DEFAULT false,renewal_stopped BOOLEAN NOT NULL DEFAULT false,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(user_id,request_key),UNIQUE(provider,provider_reference));
CREATE UNIQUE INDEX billing_one_open_purchase ON billing_purchase(user_id) WHERE status NOT IN ('CANCELED','EXPIRED','REVOKED');
CREATE INDEX billing_reconcile ON billing_purchase(updated_at);
CREATE TABLE billing_event(provider VARCHAR(16) NOT NULL,event_id VARCHAR(160) NOT NULL,purchase_id UUID NOT NULL REFERENCES billing_purchase(id) ON DELETE CASCADE,event_type VARCHAR(80) NOT NULL,received_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(provider,event_id));
CREATE TABLE billing_charge(provider VARCHAR(16) NOT NULL,charge_id VARCHAR(160) NOT NULL,purchase_id UUID NOT NULL REFERENCES billing_purchase(id) ON DELETE CASCADE,amount_minor BIGINT NOT NULL CHECK(amount_minor>=0),refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK(refunded_minor>=0 AND refunded_minor<=amount_minor),currency VARCHAR(3) NOT NULL,status VARCHAR(32) NOT NULL,paid_at TIMESTAMPTZ,updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),PRIMARY KEY(provider,charge_id));
ALTER TABLE user_subscription ADD COLUMN billing_access_until TIMESTAMPTZ;
ALTER TABLE virtual_account ADD COLUMN billing_access_until TIMESTAMPTZ;
