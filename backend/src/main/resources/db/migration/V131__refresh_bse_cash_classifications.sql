-- Earlier BSE_EQ imports included debt and used the raw provider tick size.
-- Only a new master import may re-enable a supported, normalized cash listing.
UPDATE instrument
SET active = false
WHERE provider = 'UPSTOX' AND market_region = 'INDIA' AND segment = 'BSE_EQ';
