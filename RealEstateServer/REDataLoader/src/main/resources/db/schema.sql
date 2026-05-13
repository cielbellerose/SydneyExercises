CREATE TABLE IF NOT EXISTS property (
    property_id        INTEGER PRIMARY KEY,
    download_date      TEXT,
    council_name       TEXT,
    purchase_price     INTEGER,
    address            TEXT,
    post_code          TEXT,
    property_type      TEXT,
    strata_lot_number  TEXT,
    property_name      TEXT,
    area               REAL,
    area_type          TEXT,
    contract_date      TEXT,
    settlement_date    TEXT,
    zoning             TEXT,
    nature_of_property TEXT,
    primary_purpose    TEXT,
    legal_description  TEXT,
    -- SQLite has no boolean type, so 0/1 in an INTEGER column.
    for_sale           INTEGER NOT NULL DEFAULT 0
);
