ALTER TABLE virtual_account
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING TRIM(currency);

ALTER TABLE instrument
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING TRIM(currency);
