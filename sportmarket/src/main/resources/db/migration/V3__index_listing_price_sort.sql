-- Complements the existing (status, created_at DESC, id DESC) index.
-- A forward/backward scan supports price_asc/price_desc with deterministic ID ties.
CREATE INDEX idx_listings_status_price_id ON listings(status, price, id);
