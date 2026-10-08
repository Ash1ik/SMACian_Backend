-- V3: fix blood_request_images.image_data type (BYTEA -> OID).
--
-- V2 created the column as BYTEA, but Hibernate maps @Lob ByteArray to OID
-- on PostgreSQL (like post_images.image_data and the users photo columns).
-- bytea cannot be CAST to oid, so this rebuilds the empty column instead of
-- altering it. Safe: blood requests were not live yet - the table holds no
-- rows on any database where V2 already ran (verified before shipping V3).
-- Fresh databases get OID directly from the corrected V2; this V3 is a
-- harmless re-creation there too.

ALTER TABLE IF EXISTS blood_request_images DROP COLUMN IF EXISTS image_data;
ALTER TABLE IF EXISTS blood_request_images ADD COLUMN image_data OID NOT NULL;
