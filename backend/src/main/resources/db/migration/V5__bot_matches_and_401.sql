-- 1.6.0: practice matches against Trafo, the bot, and 401 as a start score.

-- ------------------------------------------------------------------ 401
alter table matches drop constraint matches_start_score_check;
alter table matches add constraint matches_start_score_check check (start_score in (301, 401, 501, 701));
alter table tournaments drop constraint tournaments_start_score_check;
alter table tournaments add constraint tournaments_start_score_check check (start_score in (301, 401, 501, 701));
alter table challenges drop constraint challenges_start_score_check;
alter table challenges add constraint challenges_start_score_check check (start_score in (301, 401, 501, 701));

-- ------------------------------------------------------------------ bot matches
-- Kept apart from matches on purpose: nothing that reads matches (rating, leaderboard, head-to-head, awards,
-- achievements, recap, the live feed) can see them. Darts are the source of truth and the state is replayed from them;
-- the player is always side 0, Trafo side 1. The player's numbers are stored when the match ends, for the bot record.
create table bot_matches (
    id                 uuid         primary key,
    user_id            bigint       not null references users (id),
    level              varchar(16)  not null check (level in ('BEGINNER', 'CASUAL', 'CLUB', 'LEAGUE', 'PRO')),
    start_score        integer      not null check (start_score in (301, 401, 501, 701)),
    in_rule            varchar(16)  not null,
    out_rule           varchar(16)  not null,
    legs_to_win        integer      not null check (legs_to_win between 1 and 11),
    starting_player    smallint     not null check (starting_player in (0, 1)),
    seed               bigint       not null,
    status             varchar(16)  not null check (status in ('IN_PROGRESS', 'COMPLETED', 'FORFEITED')),
    won                boolean,
    legs_won           integer      not null default 0,
    legs_lost          integer      not null default 0,
    darts_thrown       integer      not null default 0,
    points_scored      integer      not null default 0,
    first9_darts       integer      not null default 0,
    first9_points      integer      not null default 0,
    count_180          integer      not null default 0,
    count_140          integer      not null default 0,
    count_100          integer      not null default 0,
    highest_turn       integer      not null default 0,
    highest_checkout   integer      not null default 0,
    checkout_attempts  integer      not null default 0,
    checkouts_hit      integer      not null default 0,
    bot_average        numeric(6, 2) not null default 0,
    created_at         timestamptz  not null default now(),
    completed_at       timestamptz,
    version            bigint       not null default 0,
    constraint ck_bot_match_result check ((status = 'IN_PROGRESS') = (won is null))
);
create index ix_bot_matches_user on bot_matches (user_id, completed_at);
-- One match against Trafo at a time per player.
create unique index ux_bot_matches_one_open on bot_matches (user_id) where status = 'IN_PROGRESS';

create table bot_match_darts (
    match_id    uuid      not null references bot_matches (id) on delete cascade,
    seq         integer   not null check (seq >= 1),
    segment     integer   not null check (segment between 0 and 25),
    multiplier  integer   not null check (multiplier between 0 and 3),
    primary key (match_id, seq)
);
