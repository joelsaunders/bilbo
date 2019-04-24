alter table deposits
drop constraint deposits_bill_id_fkey,
add constraint deposits_bill_id_fkey
    foreign key (bill_id)
    references bills(id)
    on delete cascade;

alter table withdrawals
drop constraint withdrawals_bill_id_fkey,
add constraint withdrawals_bill_id_fkey
    foreign key (bill_id)
    references bills(id)
    on delete cascade;
