-- TrafonDart initial schema. See docs/DATABASE.md for the ER diagram.

-- ---------------------------------------------------------------- users & invites
create table users (
    id                bigserial primary key,
    username          varchar(24)      not null,
    real_name         varchar(60)      not null,
    password_hash     varchar(100)     not null,
    role              varchar(16)      not null default 'USER' check (role in ('USER', 'ADMIN')),
    enabled           boolean          not null default true,
    token_version     integer          not null default 0,
    preferred_locale  varchar(5)       check (preferred_locale in ('nb', 'en')),
    rating            double precision not null default 1500,
    rating_deviation  double precision not null default 350,
    volatility        double precision not null default 0.06,
    last_rated_at     timestamptz,
    created_at        timestamptz      not null default now()
);
create unique index ux_users_username_lower on users (lower(username));

create table invite_codes (
    id          bigserial primary key,
    code_hash   varchar(64)  not null unique,
    code_hint   varchar(8)   not null,
    max_uses    integer      not null check (max_uses between 1 and 100),
    uses        integer      not null default 0 check (uses >= 0),
    expires_at  timestamptz  not null,
    revoked     boolean      not null default false,
    note        varchar(100),
    created_by  bigint       not null references users (id),
    created_at  timestamptz  not null default now(),
    constraint ck_invite_uses check (uses <= max_uses)
);

create table invite_code_redemptions (
    id              bigserial primary key,
    invite_code_id  bigint      not null references invite_codes (id),
    user_id         bigint      not null unique references users (id) on delete cascade,
    redeemed_at     timestamptz not null default now()
);
create index ix_redemptions_code on invite_code_redemptions (invite_code_id);

-- ---------------------------------------------------------------- tournaments
create table tournaments (
    id            uuid primary key,
    name          varchar(60)  not null,
    format        varchar(24)  not null check (format in ('ROUND_ROBIN', 'SINGLE_ELIMINATION')),
    status        varchar(16)  not null check (status in ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    start_score   integer      not null check (start_score in (301, 501, 701)),
    in_rule       varchar(16)  not null check (in_rule in ('STRAIGHT', 'DOUBLE')),
    out_rule      varchar(16)  not null check (out_rule in ('STRAIGHT', 'DOUBLE', 'MASTER')),
    legs_to_win   integer      not null check (legs_to_win between 1 and 11),
    rated         boolean      not null default true,
    created_by    bigint       not null references users (id),
    winner_id     bigint       references users (id),
    created_at    timestamptz  not null default now(),
    started_at    timestamptz,
    completed_at  timestamptz
);
create index ix_tournaments_status on tournaments (status, created_at desc);

create table tournament_participants (
    tournament_id  uuid    not null references tournaments (id) on delete cascade,
    user_id        bigint  not null references users (id),
    seed           integer not null,
    primary key (tournament_id, user_id)
);

-- ---------------------------------------------------------------- matches
create table matches (
    id               uuid primary key,
    start_score      integer      not null check (start_score in (301, 501, 701)),
    in_rule          varchar(16)  not null check (in_rule in ('STRAIGHT', 'DOUBLE')),
    out_rule         varchar(16)  not null check (out_rule in ('STRAIGHT', 'DOUBLE', 'MASTER')),
    legs_to_win      integer      not null check (legs_to_win between 1 and 11),
    player1_id       bigint       not null references users (id),
    player2_id       bigint       not null references users (id),
    starting_player  smallint     not null check (starting_player in (0, 1)),
    status           varchar(16)  not null check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'VOIDED')),
    rated            boolean      not null,
    winner_id        bigint       references users (id),
    created_by       bigint       not null references users (id),
    tournament_id    uuid         references tournaments (id),
    created_at       timestamptz  not null default now(),
    completed_at     timestamptz,
    version          bigint       not null default 0,
    constraint ck_match_distinct_players check (player1_id <> player2_id)
);
create index ix_matches_status on matches (status, created_at desc);
create index ix_matches_player1 on matches (player1_id, completed_at desc);
create index ix_matches_player2 on matches (player2_id, completed_at desc);
create index ix_matches_completed on matches (completed_at) where status = 'COMPLETED';

-- Every dart thrown. The single source of truth for a match; everything else is derived.
create table darts (
    id          bigserial primary key,
    match_id    uuid        not null references matches (id) on delete cascade,
    seq         integer     not null check (seq >= 1),
    player_id   bigint      not null references users (id),
    segment     smallint    not null check (segment between 0 and 20 or segment = 25),
    multiplier  smallint    not null check (multiplier between 0 and 3),
    created_at  timestamptz not null default now(),
    constraint ux_darts_match_seq unique (match_id, seq),
    constraint ck_dart_valid check (
        (segment = 0 and multiplier = 0)
        or (segment = 25 and multiplier in (1, 2))
        or (segment between 1 and 20 and multiplier between 1 and 3))
);

-- Written when a match completes.
create table legs (
    id                bigserial primary key,
    match_id          uuid     not null references matches (id) on delete cascade,
    leg_no            integer  not null,
    starter_id        bigint   not null references users (id),
    winner_id         bigint   not null references users (id),
    winner_darts      integer  not null,
    checkout          integer  not null,
    constraint ux_legs_match_leg unique (match_id, leg_no)
);

create table match_player_stats (
    match_id           uuid    not null references matches (id) on delete cascade,
    user_id            bigint  not null references users (id),
    legs_won           integer not null,
    darts_thrown       integer not null,
    points_scored      integer not null,
    first9_darts       integer not null,
    first9_points      integer not null,
    count_180          integer not null,
    count_140          integer not null,
    count_100          integer not null,
    highest_turn       integer not null,
    highest_checkout   integer not null,
    checkout_attempts  integer not null,
    checkouts_hit      integer not null,
    primary key (match_id, user_id)
);
create index ix_mps_user on match_player_stats (user_id);

create table rating_history (
    id               bigserial primary key,
    user_id          bigint           not null references users (id),
    match_id         uuid             not null references matches (id) on delete cascade,
    rating_before    double precision not null,
    rating_after     double precision not null,
    rd_before        double precision not null,
    rd_after         double precision not null,
    volatility_after double precision not null,
    rated_at         timestamptz      not null,
    constraint ux_rating_history unique (user_id, match_id)
);
create index ix_rating_history_user on rating_history (user_id, rated_at);

create table match_disputes (
    id               bigserial primary key,
    match_id         uuid         not null references matches (id) on delete cascade,
    raised_by        bigint       not null references users (id),
    reason           varchar(500) not null,
    status           varchar(16)  not null check (status in ('OPEN', 'REJECTED', 'UPHELD')),
    resolved_by      bigint       references users (id),
    resolution_note  varchar(500),
    created_at       timestamptz  not null default now(),
    resolved_at      timestamptz
);
create unique index ux_one_open_dispute_per_match on match_disputes (match_id) where status = 'OPEN';

-- ---------------------------------------------------------------- tournament fixtures
create table tournament_fixtures (
    id               bigserial primary key,
    tournament_id    uuid        not null references tournaments (id) on delete cascade,
    round            integer     not null,
    position         integer     not null,
    player1_id       bigint      references users (id),
    player2_id       bigint      references users (id),
    match_id         uuid        unique references matches (id),
    winner_id        bigint      references users (id),
    status           varchar(16) not null check (status in ('PENDING', 'READY', 'IN_PROGRESS', 'COMPLETED', 'BYE')),
    next_fixture_id  bigint      references tournament_fixtures (id),
    next_slot        smallint    check (next_slot in (1, 2)),
    constraint ux_fixture_position unique (tournament_id, round, position)
);
create index ix_fixtures_tournament on tournament_fixtures (tournament_id, round, position);
