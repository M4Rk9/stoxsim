CREATE TABLE derivative_basket (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES derivative_wallet(user_id) ON DELETE CASCADE,
    request_key UUID NOT NULL,
    signature VARCHAR(500) NOT NULL,
    filled_at TIMESTAMPTZ NOT NULL,
    UNIQUE(user_id, request_key)
);
ALTER TABLE derivative_fill ADD COLUMN basket_id UUID REFERENCES derivative_basket(id) ON DELETE CASCADE;
CREATE INDEX derivative_basket_fills ON derivative_fill(basket_id) WHERE basket_id IS NOT NULL;
