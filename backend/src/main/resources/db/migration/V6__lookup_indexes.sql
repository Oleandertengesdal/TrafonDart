-- Indexes for lookups that had none (found in the November 2026 audit). No data changes.

-- Rating changes of a set of matches: the year recap, achievements and the match breakdown (win probability).
-- The unique (user_id, match_id) constraint cannot serve a lookup by match alone.
create index ix_rating_history_match on rating_history (match_id);

-- Rating changes inside a month, for the monthly standings (ix_rating_history_user starts with user_id).
create index ix_rating_history_rated_at on rating_history (rated_at);

-- "Latest results" on the home page, the TV and the match history: newest finished first.
create index ix_matches_recent on matches ((coalesce(completed_at, created_at)) desc);

-- Disputes of one match (the partial unique index only covers the open one).
create index ix_disputes_match on match_disputes (match_id);
