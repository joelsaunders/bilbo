create TABLE DEPOSITS (
    id serial primary key,
    amount integer,
    bill_id integer references bills (id),
    deposit_date timestamp
)