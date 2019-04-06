create TABLE WITHDRAWALS (
    id serial primary key,
    withdrawal_date timestamp,
    success boolean,
    bill_id integer references bills (id)
);