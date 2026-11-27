ALTER TABLE billing_purchase
    ADD COLUMN agreed_amount_paise integer CHECK (agreed_amount_paise > 0),
    ADD COLUMN play_base_plan varchar(150);

UPDATE billing_purchase
SET agreed_amount_paise = CASE plan WHEN 'PLUS' THEN 9900 WHEN 'PRO' THEN 19900 END
WHERE provider = 'RAZORPAY';
