create table family_ai_pool_usage (
    period_key varchar(7) primary key,
    spent_microrupees bigint not null default 0,
    updated_at timestamp not null default current_timestamp
);

create table family_ai_user_usage (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    period_key varchar(7) not null,
    spent_microrupees bigint not null default 0,
    updated_at timestamp not null default current_timestamp,
    constraint uq_family_ai_user_period unique (user_id, period_key)
);

create index idx_family_ai_user_usage_period on family_ai_user_usage(period_key);
