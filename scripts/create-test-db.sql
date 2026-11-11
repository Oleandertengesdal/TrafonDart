-- Runs once when the local Docker database is first created.
-- A separate database for the backend integration tests (they wipe it on every run).
CREATE DATABASE trafondart_test;
