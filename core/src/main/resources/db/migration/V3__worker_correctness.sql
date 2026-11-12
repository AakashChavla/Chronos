ALTER TABLE job_definition
    ADD COLUMN backoff_base_sec double precision NOT NULL DEFAULT 1 CHECK (backoff_base_sec >= 0),
    ADD COLUMN backoff_max_sec double precision NOT NULL DEFAULT 300 CHECK (backoff_max_sec >= 0),
    ADD COLUMN backoff_jitter double precision NOT NULL DEFAULT 1 CHECK (backoff_jitter >= 0),
    ADD COLUMN priority smallint NOT NULL DEFAULT 0 CHECK (priority BETWEEN 0 AND 9);

ALTER TABLE job_run
    ADD COLUMN cancel_requested boolean NOT NULL DEFAULT false;

CREATE TABLE run_event (
    id bigserial PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES job_run(id) ON DELETE CASCADE,
    ts timestamptz NOT NULL DEFAULT now(),
    type text NOT NULL,
    worker_id text,
    attempt int NOT NULL,
    detail text
);

CREATE INDEX run_event_run_id_ts_idx ON run_event(run_id, ts, id);

INSERT INTO run_event(run_id, type, worker_id, attempt, detail)
SELECT id, 'QUEUED', locked_by, attempt, 'Backfilled before run event tracking.'
FROM job_run;
CREATE OR REPLACE FUNCTION chronos_log_job_run_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    event_type text;
    event_worker_id text;
BEGIN
    IF TG_OP = 'INSERT' THEN
        event_type := 'QUEUED';
        event_worker_id := NEW.locked_by;
    ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
        event_worker_id := COALESCE(NEW.locked_by, OLD.locked_by);
        event_type := CASE NEW.status
            WHEN 'RUNNING' THEN 'CLAIMED'
            WHEN 'SUCCEEDED' THEN 'SUCCEEDED'
            WHEN 'FAILED' THEN 'FAILED'
            WHEN 'DEAD' THEN 'DEAD'
            WHEN 'CANCELLED' THEN 'CANCELLED'
            WHEN 'SKIPPED' THEN 'SKIPPED'
            WHEN 'QUEUED' THEN CASE
                WHEN OLD.status = 'RUNNING' AND NEW.error = 'worker shutdown release' THEN 'RELEASED'
                WHEN OLD.status = 'RUNNING' AND NEW.error = 'worker lease expired' THEN 'LEASE_EXPIRED'
                WHEN OLD.status = 'RUNNING' THEN 'RETRY_SCHEDULED'
                ELSE 'QUEUED'
            END
            ELSE NEW.status
        END;
    ELSE
        RETURN NEW;
    END IF;

    INSERT INTO run_event(run_id, type, worker_id, attempt, detail)
    VALUES (
        NEW.id,
        event_type,
        event_worker_id,
        NEW.attempt,
        NEW.error);
    RETURN NEW;
END;
$$;

CREATE TRIGGER job_run_transition_event
AFTER INSERT OR UPDATE ON job_run
FOR EACH ROW
EXECUTE FUNCTION chronos_log_job_run_transition();
