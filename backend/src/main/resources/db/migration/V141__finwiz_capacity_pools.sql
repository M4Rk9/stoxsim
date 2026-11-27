ALTER TABLE finwiz_request_reservation
    ADD COLUMN depth varchar(8) CHECK (depth IN ('BASIC','EXPANDED','FULL'));
ALTER TABLE finwiz_provider_admission
    ADD COLUMN depth varchar(8) CHECK (depth IN ('BASIC','EXPANDED','FULL'));
CREATE INDEX idx_finwiz_admission_depth ON finwiz_provider_admission(provider,depth,admitted_at);

CREATE TABLE finwiz_pool_usage_daily (
    usage_day date NOT NULL,
    depth varchar(8) NOT NULL CHECK (depth IN ('BASIC','EXPANDED','FULL')),
    provider varchar(24) NOT NULL,
    provider_attempts integer NOT NULL CHECK (provider_attempts > 0),
    reserved_tokens bigint NOT NULL CHECK (reserved_tokens > 0),
    PRIMARY KEY (usage_day,depth,provider)
);
