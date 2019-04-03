create TABLE BILLS (
    id serial primary key,
    user_id integer references users (id),
    "name" varchar(100),
    amount integer,
    due_date timestamp,
    UNIQUE (user_id, "name")
)