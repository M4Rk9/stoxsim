-- Earlier imports treated every NSE_EQ series as equity, including bonds.
-- Quarantine known non-equity ISIN classes immediately; the next complete
-- master sync also removes unsupported series using the provider's series field.
UPDATE instrument SET active = false
WHERE provider = 'UPSTOX' AND market_region = 'INDIA' AND segment = 'NSE_EQ'
  AND isin IS NOT NULL
  AND NOT (isin LIKE 'INF%' OR (isin LIKE 'INE%' AND substring(isin FROM 8 FOR 2) = '01'));

-- NSE ETFs use the EQ series with a fund ISIN, not security_type=ETF.
UPDATE instrument SET instrument_type = 'ETF'
WHERE provider = 'UPSTOX' AND market_region = 'INDIA' AND segment = 'NSE_EQ'
  AND isin LIKE 'INF%' AND instrument_type = 'EQUITY';
