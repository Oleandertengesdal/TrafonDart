-- 1.7.0: a finished normal match counts only once it is confirmed (by the other player, an admin, or by itself after
-- two days), and a match can be registered with only its result. Tournament matches are confirmed when they end.

-- UNCONFIRMED: played to the end (or registered as a result) but not counted yet.
alter table matches drop constraint matches_status_check;
alter table matches add constraint matches_status_check
    check (status in ('IN_PROGRESS', 'UNCONFIRMED', 'COMPLETED', 'ABANDONED', 'VOIDED'));

-- Registered with only the result: no darts, legs or throwing stats, only legs won.
alter table matches add column result_only boolean not null default false;
alter table matches add constraint ck_match_result_only_not_in_tournament check (not result_only or tournament_id is null);

-- When the match started to count, and who confirmed it (null: confirmed automatically, or before 1.7.0).
-- Ratings are applied and rebuilt in this order.
alter table matches add column confirmed_at timestamptz;
alter table matches add column confirmed_by bigint references users (id);

-- Every match that already counted stays counted, at the time it finished.
update matches set confirmed_at = completed_at where status in ('COMPLETED', 'VOIDED') and confirmed_at is null;

-- The automatic confirmation looks for unconfirmed matches by the time they finished.
create index ix_matches_unconfirmed on matches (completed_at) where status = 'UNCONFIRMED';
