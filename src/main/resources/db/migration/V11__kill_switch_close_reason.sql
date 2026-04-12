alter table orders
    add column if not exists close_reason varchar(64);

alter table algo_orders
    add column if not exists close_reason varchar(64);
