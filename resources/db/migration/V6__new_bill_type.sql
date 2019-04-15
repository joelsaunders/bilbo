ALTER TABLE bills
ALTER COLUMN due_day_of_month DROP NOT NULL,
ADD COLUMN bill_type varchar(10) NULL,
ADD COLUMN period integer NULL,
ADD COLUMN start_date timestamp NULL;

UPDATE bills
SET bill_type = 'monthly';

ALTER TABLE bills
ALTER COLUMN bill_type DROP NOT NULL;