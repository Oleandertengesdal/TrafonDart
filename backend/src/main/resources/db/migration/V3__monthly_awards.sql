-- Monthly awards: Player of the Month plus category awards, decided after each calendar month (Europe/Oslo).

create table award_periods (
    period        varchar(7)  primary key,          -- 'YYYY-MM'
    finalized_at  timestamptz not null default now()
);

create table monthly_awards (
    id          bigserial primary key,
    period      varchar(7)       not null references award_periods (period) on delete cascade,
    award       varchar(24)      not null check (award in
                    ('PLAYER_OF_MONTH', 'MOST_IMPROVED', 'MOST_ACTIVE', 'MOST_180S', 'HIGHEST_CHECKOUT')),
    user_id     bigint           not null references users (id) on delete cascade,
    value       double precision not null,          -- points, rating gain, matches, 180s or checkout
    created_at  timestamptz      not null default now(),
    constraint ux_award_per_period unique (period, award)
);
create index ix_awards_user on monthly_awards (user_id, period desc);
