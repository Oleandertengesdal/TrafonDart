-- LOCAL DEMO DATA ONLY. Moves the 32 oldest non-tournament matches into the two previous months so the
-- Player of the Month hall of fame and trophy cabinets have something to show.
-- Afterwards, as admin: recompute ratings and then the monthly awards.
--
--   psql "postgresql://trafondart:<pw>@localhost:5432/trafondart" -f scripts/backdate-demo.sql
begin;

create temp table shift as
select id, completed_at as old_at,
  case when rn <= 16
       then date_trunc('month', now()) - interval '2 months' + interval '1 day 19 hours' + (rn - 1) * interval '1 day 15 hours'
       else date_trunc('month', now()) - interval '1 month' + interval '19 hours' + (rn - 17) * interval '1 day 16 hours'
  end as new_at
from (select id, completed_at, row_number() over (order by completed_at) rn
      from matches where status = 'COMPLETED' and tournament_id is null) x
where rn <= 32;

update matches m set completed_at = s.new_at, created_at = s.new_at - (m.completed_at - m.created_at)
from shift s where m.id = s.id;
update darts d set created_at = d.created_at + (s.new_at - s.old_at) from shift s where d.match_id = s.id;
update users set created_at = date_trunc('month', now()) - interval '2 months' - interval '3 days';

select to_char(completed_at, 'YYYY-MM') as month, count(*) from matches where status = 'COMPLETED' group by 1 order by 1;
commit;
