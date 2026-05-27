-- V2: Align schema with JPA entity definitions
--
-- V1 was missing several columns that were added to the entities during the initial
-- auth model design. This migration adds those columns without modifying V1 so that
-- the Flyway checksum on V1 is never disturbed.

-- users: track whether the email has been verified through an external provider.
-- For Google sign-in users this will always be true at creation time, since
-- the Google token verifier already enforces email_verified = true.
-- Default FALSE so the column is safe to add to a table that may already have rows.
ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- user_auth_identities: record the email address the user had at the time of
-- each authentication event. Google email can change in rare cases; this column
-- gives us an audit trail of what email was presented per auth record.
ALTER TABLE user_auth_identities
    ADD COLUMN email_at_auth_time VARCHAR(255);

-- Performance index: identity lookups by (provider, provider_subject) are on the
-- hot path for every sign-in. The UNIQUE constraint already creates an index in
-- PostgreSQL, but we explicitly document it here for clarity. No additional index
-- is needed beyond what the UNIQUE constraint already provides.
--
-- No additional DDL needed here; index idx_user_auth_identities_user_id from V1
-- already covers the JOIN from user_auth_identities → users.
