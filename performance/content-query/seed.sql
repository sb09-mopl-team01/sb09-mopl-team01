\set ON_ERROR_STOP on
\timing on

DO $$
BEGIN
    IF current_database() <> 'mopl_perf' THEN
        RAISE EXCEPTION 'Performance seed must run only against the mopl_perf database.';
    END IF;
END
$$;

BEGIN;
SET LOCAL synchronous_commit = off;

TRUNCATE TABLE review, watching_sessions, content_tags, contents CASCADE;
DELETE FROM users WHERE email LIKE 'perf-user-%@example.test';

INSERT INTO users (
    id, created_at, updated_at, email, password_hash,
    name, profile_image_url, role, locked
)
SELECT
    md5('perf-user-' || n)::uuid,
    TIMESTAMPTZ '2025-01-01 00:00:00+09' + (n % 365) * INTERVAL '1 day',
    TIMESTAMPTZ '2025-01-01 00:00:00+09' + (n % 365) * INTERVAL '1 day',
    'perf-user-' || n || '@example.test',
    'not-used-for-performance-login',
    U&'\C131\B2A5 \C0AC\C6A9\C790 ' || n,
    NULL,
    'USER',
    FALSE
FROM generate_series(1, 50000) AS series(n);

INSERT INTO contents (
    id, created_at, updated_at, type, title, description,
    thumbnail_url, thumbnail_key, source, external_id,
    last_synced_at, average_rating, review_count
)
SELECT
    md5('perf-content-' || n)::uuid,
    TIMESTAMPTZ '2025-01-01 00:00:00+09'
        + (n % 365) * INTERVAL '1 day'
        + (n % 86400) * INTERVAL '1 second',
    TIMESTAMPTZ '2025-01-01 00:00:00+09'
        + (n % 365) * INTERVAL '1 day'
        + (n % 86400) * INTERVAL '1 second',
    CASE n % 3
      WHEN 0 THEN 'movie'
      WHEN 1 THEN 'tvSeries'
      ELSE 'sport'
    END,
    CASE n % 3
      WHEN 0 THEN U&'\C601\D654 \CF58\D150\CE20 '
      WHEN 1 THEN U&'TV \C2DC\B9AC\C988 \CF58\D150\CE20 '
      ELSE U&'\C2A4\D3EC\CE20 \CF58\D150\CE20 '
    END || lpad(n::text, 6, '0'),
    CASE
      WHEN n % 100 = 0 THEN U&'\C6B0\C8FC \BAA8\D5D8\ACFC \C131\C7A5 \C774\C57C\AE30\B97C \B2E4\B8E8\BA70 \AC80\C0C9 \C131\B2A5 \AC80\C99D \CF58\D150\CE20\C785\B2C8\B2E4.'
      WHEN n % 50 = 0 THEN U&'\B85C\B9E8\C2A4\C640 \AC00\C871 \C774\C57C\AE30\B97C \B2E4\B8E8\BA70 \AC80\C0C9 \C131\B2A5 \AC80\C99D \CF58\D150\CE20\C785\B2C8\B2E4.'
      WHEN n % 25 = 0 THEN U&'\CD95\AD6C \ACBD\AE30\C640 \C120\C218 \AE30\B85D\C744 \B2E4\B8E8\BA70 \AC80\C0C9 \C131\B2A5 \AC80\C99D \CF58\D150\CE20\C785\B2C8\B2E4.'
      ELSE U&'\B300\C6A9\B7C9 \CF58\D150\CE20 \BAA9\B85D\ACFC \CEE4\C11C \D398\C774\C9C0\B124\C774\C158 \C131\B2A5\C744 \AC80\C99D\D558\AE30 \C704\D55C \C124\BA85\C785\B2C8\B2E4.'
    END,
    CASE n % 3
      WHEN 0 THEN 'https://placehold.co/300x450/png?text=Movie'
      WHEN 1 THEN 'https://placehold.co/300x450/png?text=TV'
      ELSE 'https://placehold.co/300x450/png?text=Sport'
    END,
    NULL,
    'MANUAL',
    NULL,
    NULL,
    0.0,
    0
FROM generate_series(1, 100000) AS series(n);

INSERT INTO content_tags (content_id, tag)
SELECT
    md5('perf-content-' || n)::uuid,
    CASE n % 3
      WHEN 0 THEN U&'\C601\D654'
      WHEN 1 THEN U&'TV \C2DC\B9AC\C988'
      ELSE U&'\C2A4\D3EC\CE20'
    END
FROM generate_series(1, 100000) AS series(n)
UNION ALL
SELECT
    md5('perf-content-' || n)::uuid,
    (ARRAY[
      U&'\C561\C158', U&'\B4DC\B77C\B9C8', U&'\CF54\BBF8\B514', U&'\B85C\B9E8\C2A4', U&'\D310\D0C0\C9C0',
      'SF', U&'\C2A4\B9B4\B7EC', U&'\B2E4\D050\BA58\D130\B9AC', U&'\AC00\C871', U&'\C560\B2C8\BA54\C774\C158',
      U&'\CD95\AD6C', U&'\C57C\AD6C', U&'\B18D\AD6C', U&'\BC30\AD6C', U&'\D14C\B2C8\C2A4',
      U&'\BAA8\D130\C2A4\D3EC\CE20', U&'e\C2A4\D3EC\CE20', U&'\BC94\C8C4', U&'\BBF8\C2A4\D130\B9AC', U&'\C5ED\C0AC'
    ])[1 + (n % 20)]
FROM generate_series(1, 100000) AS series(n)
UNION ALL
SELECT
    md5('perf-content-' || n)::uuid,
    CASE WHEN n % 10 = 0 THEN U&'\C778\AE30' ELSE U&'\C77C\BC18' END
FROM generate_series(1, 100000) AS series(n);

WITH generated AS (
  SELECT
      n,
      ((n - 1) % 100000) + 1 AS content_no,
      ((n - 1) / 100000)::integer AS reviewer_slot
  FROM generate_series(1, 300000) AS series(n)
)
INSERT INTO review (
    id, created_at, updated_at, user_id, content_id, text, rating
)
SELECT
    md5('perf-review-' || n)::uuid,
    TIMESTAMPTZ '2025-06-01 00:00:00+09'
        + (content_no % 300) * INTERVAL '1 day'
        + reviewer_slot * INTERVAL '1 minute',
    TIMESTAMPTZ '2025-06-01 00:00:00+09'
        + (content_no % 300) * INTERVAL '1 day'
        + reviewer_slot * INTERVAL '1 minute',
    md5(
        'perf-user-'
        || (((content_no + reviewer_slot * 7919 - 1) % 50000) + 1)
    )::uuid,
    md5('perf-content-' || content_no)::uuid,
    U&'\C131\B2A5 \AC80\C99D\C6A9 \B9AC\BDF0 ' || n,
    (((content_no + reviewer_slot * 2) % 5) + 1)::double precision
FROM generated;

UPDATE contents AS content
SET
    average_rating = stats.average_rating,
    review_count = stats.review_count
FROM (
    SELECT
        content_id,
        avg(rating)::double precision AS average_rating,
        count(*)::integer AS review_count
    FROM review
    GROUP BY content_id
) AS stats
WHERE content.id = stats.content_id;

WITH generated AS (
  SELECT
      n,
      CASE
        WHEN n <= 10000 THEN ((n - 1) % 100) + 1
        WHEN n <= 15000 THEN 101 + ((n - 10001) % 400)
        ELSE 501 + ((n - 15001) % 9500)
      END AS content_no
  FROM generate_series(1, 20000) AS series(n)
)
INSERT INTO watching_sessions (
    id, created_at, watcher_id, content_id
)
SELECT
    md5('perf-watching-session-' || n)::uuid,
    TIMESTAMPTZ '2026-07-01 00:00:00+09' + (n % 86400) * INTERVAL '1 second',
    md5('perf-user-' || n)::uuid,
    md5('perf-content-' || content_no)::uuid
FROM generated;

COMMIT;

ANALYZE users;
ANALYZE contents;
ANALYZE content_tags;
ANALYZE review;
ANALYZE watching_sessions;

SELECT 'users' AS table_name, count(*) AS row_count FROM users
UNION ALL
SELECT 'contents', count(*) FROM contents
UNION ALL
SELECT 'content_tags', count(*) FROM content_tags
UNION ALL
SELECT 'review', count(*) FROM review
UNION ALL
SELECT 'watching_sessions', count(*) FROM watching_sessions
ORDER BY table_name;
