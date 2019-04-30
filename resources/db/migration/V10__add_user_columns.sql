ALTER TABLE users
ADD COLUMN "active" boolean NOT NULL DEFAULT TRUE,
ADD COLUMN whitelisted boolean NOT NULL DEFAULT FALSE;

UPDATE users
SET whitelisted = true;

ALTER TABLE bills
ALTER COLUMN period_type SET NOT NULL,
ALTER COLUMN period_frequency SET NOT NULL,
ALTER COLUMN start_date SET NOT NULL;