ALTER TABLE job_run ADD COLUMN lease_token uuid;

CREATE INDEX job_run_lease_token_idx ON job_run (lease_token) WHERE lease_token IS NOT NULL;
