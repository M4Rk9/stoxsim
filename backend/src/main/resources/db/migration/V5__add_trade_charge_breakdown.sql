ALTER TABLE trade
    ADD COLUMN brokerage NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN stt NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN exchange_charges NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN gst NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN sebi_charges NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN stamp_duty NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN dp_charges NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN net_cash_effect NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN charge_schedule_version VARCHAR(48) NOT NULL DEFAULT 'LEGACY-ZERO',
    ADD COLUMN charges_simulated BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE trade
    ADD CONSTRAINT ck_trade_charge_breakdown CHECK (
        brokerage >= 0
        AND stt >= 0
        AND exchange_charges >= 0
        AND gst >= 0
        AND sebi_charges >= 0
        AND stamp_duty >= 0
        AND dp_charges >= 0
        AND net_cash_effect >= 0
    );
