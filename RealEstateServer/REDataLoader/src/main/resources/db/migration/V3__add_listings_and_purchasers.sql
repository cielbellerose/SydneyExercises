CREATE TABLE IF NOT EXISTS listing (
    listing_id   BIGSERIAL PRIMARY KEY,
    property_id  INTEGER NOT NULL,
    listed_date  TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'active',
    created_at   TIMESTAMPTZ DEFAULT now(),
    FOREIGN KEY (property_id) REFERENCES property(property_id)
);

CREATE INDEX IF NOT EXISTS idx_listing_property ON listing(property_id);
CREATE INDEX IF NOT EXISTS idx_listing_status   ON listing(status);

CREATE TABLE IF NOT EXISTS listing_price (
    listing_price_id  BIGSERIAL PRIMARY KEY,
    listing_id        BIGINT NOT NULL,
    price             BIGINT NOT NULL,
    effective_date    TEXT NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT now(),
    FOREIGN KEY (listing_id) REFERENCES listing(listing_id)
);

CREATE INDEX IF NOT EXISTS idx_lp_listing ON listing_price(listing_id);

CREATE TABLE IF NOT EXISTS purchaser (
    purchaser_id  BIGSERIAL PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchaser_interest (
    purchaser_id  BIGINT NOT NULL,
    postcode      TEXT NOT NULL,
    PRIMARY KEY (purchaser_id, postcode),
    FOREIGN KEY (purchaser_id) REFERENCES purchaser(purchaser_id)
);

CREATE INDEX IF NOT EXISTS idx_pi_postcode ON purchaser_interest(postcode);
