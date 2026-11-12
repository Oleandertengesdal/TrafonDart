-- 1.4.0: party games (multi-player and doubles X01, Cricket, Killer, Around the Clock), challenges,
-- Player of the Year, and account deletion.

-- ------------------------------------------------------------------ account deletion
-- A deleted account keeps its id (so opponents' match history stays intact) but loses its name and can never log in.
alter table users add column deleted_at timestamptz;
alter table users add constraint ck_users_deleted_disabled check (deleted_at is null or enabled = false);

-- ------------------------------------------------------------------ party games
-- Never rated. Darts are the source of truth, exactly like matches: state is replayed from game_darts.
create table games (
    id            uuid primary key,
    game_type     varchar(20)   not null check (game_type in ('X01', 'CRICKET', 'KILLER', 'AROUND_THE_CLOCK')),
    team_play     boolean       not null default false,
    settings      varchar(1000) not null,  -- JSON, validated by the engine for the game type
    status        varchar(16)   not null check (status in ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    winner_side   integer ,
    created_by    bigint        not null references users (id),
    created_at    timestamptz   not null default now(),
    completed_at  timestamptz,
    version       bigint        not null default 0
);
create index ix_games_status on games (status, created_at desc);
create index ix_games_completed on games (completed_at) where status = 'COMPLETED';

-- Throwing order is slot order. "side" is the player (individual games) or the team (doubles).
create table game_players (
    game_id  uuid     not null references games (id) on delete cascade,
    slot     integer  not null check (slot between 0 and 7),
    user_id  bigint   not null references users (id),
    side     integer  not null check (side between 0 and 7),
    primary key (game_id, slot),
    constraint ux_game_player unique (game_id, user_id)
);
create index ix_game_players_user on game_players (user_id);

create table game_darts (
    game_id     uuid        not null references games (id) on delete cascade,
    seq         integer     not null check (seq >= 1),
    segment     integer     not null,
    multiplier  integer     not null,
    created_at  timestamptz not null default now(),
    primary key (game_id, seq),
    constraint ck_game_dart_valid check (
        (segment = 0 and multiplier = 0)
        or (segment = 25 and multiplier in (1, 2))
        or (segment between 1 and 20 and multiplier between 1 and 3))
);

-- Written once when a game is completed, for statistics and achievements.
create table game_results (
    game_id       uuid     not null references games (id) on delete cascade,
    user_id       bigint   not null references users (id),
    placement     integer  not null check (placement >= 1),
    darts_thrown  integer  not null,
    score         integer  not null,  -- X01: points, Cricket: marks, Killer: lives taken, Around the Clock: targets hit
    won           boolean  not null,
    primary key (game_id, user_id)
);
create index ix_game_results_user on game_results (user_id);

-- ------------------------------------------------------------------ challenges
create table challenges (
    id             bigserial primary key,
    challenger_id  bigint       not null references users (id),
    opponent_id    bigint       not null references users (id),
    start_score    integer      not null check (start_score in (301, 501, 701)),
    in_rule        varchar(16)  not null check (in_rule in ('STRAIGHT', 'DOUBLE')),
    out_rule       varchar(16)  not null check (out_rule in ('STRAIGHT', 'DOUBLE', 'MASTER')),
    legs_to_win    integer      not null check (legs_to_win between 1 and 11),
    rated          boolean      not null,
    message        varchar(200),
    status         varchar(16)  not null check (status in ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED', 'EXPIRED', 'STARTED')),
    match_id       uuid         references matches (id) on delete set null,
    created_at     timestamptz  not null default now(),
    expires_at     timestamptz  not null,
    responded_at   timestamptz,
    constraint ck_challenge_distinct check (challenger_id <> opponent_id)
);
create index ix_challenges_opponent on challenges (opponent_id, status);
create index ix_challenges_challenger on challenges (challenger_id, status);

-- ------------------------------------------------------------------ Player of the Year
create table award_years (
    year          integer     primary key,
    finalized_at  timestamptz not null default now()
);

create table yearly_awards (
    year        integer          not null references award_years (year) on delete cascade,
    award       varchar(24)      not null check (award in ('PLAYER_OF_YEAR')),
    user_id     bigint           not null references users (id) on delete cascade,
    value       double precision not null,
    created_at  timestamptz      not null default now(),
    primary key (year, award)
);
create index ix_yearly_awards_user on yearly_awards (user_id);
