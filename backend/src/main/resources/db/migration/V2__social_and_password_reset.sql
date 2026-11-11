-- Kudos and comments on matches, likes on comments, and admin-issued password reset links.

create table match_kudos (
    match_id    uuid        not null references matches (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (match_id, user_id)
);

create table match_comments (
    id          bigserial primary key,
    match_id    uuid          not null references matches (id) on delete cascade,
    author_id   bigint        not null references users (id) on delete cascade,
    -- Replies are one level deep: a reply always points at a top-level comment.
    parent_id   bigint        references match_comments (id) on delete cascade,
    body        varchar(1000) not null,
    deleted     boolean       not null default false,
    created_at  timestamptz   not null default now(),
    edited_at   timestamptz
);
create index ix_comments_match on match_comments (match_id, created_at);
create index ix_comments_parent on match_comments (parent_id);

create table comment_likes (
    comment_id  bigint      not null references match_comments (id) on delete cascade,
    user_id     bigint      not null references users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (comment_id, user_id)
);

create table password_reset_tokens (
    id          bigserial primary key,
    user_id     bigint      not null references users (id) on delete cascade,
    token_hash  varchar(64) not null unique,   -- SHA-256 of the token; the token itself is never stored
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_by  bigint      not null references users (id),
    created_at  timestamptz not null default now()
);
create index ix_reset_tokens_user on password_reset_tokens (user_id);
