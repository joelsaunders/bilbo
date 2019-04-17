ALTER TABLE bills
ADD COLUMN period_type varchar(100) NULL,
ADD COLUMN period_frequency integer NULL,
DROP COLUMN bill_type,
DROP COLUMN period;

UPDATE bills
SET period_type = 'month',
period_frequency = 1;

UPDATE bills
SET start_date = make_date(date_part('year', current_timestamp)::integer, date_part('month', current_timestamp)::integer, due_day_of_month)
WHERE start_date IS NULL
;

ALTER TABLE bills
ALTER COLUMN period_type DROP NOT NULL,
ALTER COLUMN period_frequency DROP NOT NULL,
ALTER COLUMN start_date DROP NOT NULL,
DROP COLUMN due_day_of_month;