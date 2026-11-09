package io.chronos.core.run.repository;

import io.chronos.common.RunStatus;
import io.chronos.core.run.domain.ClaimedRun;
import io.chronos.core.run.domain.ExpiredRun;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRunTransitionRepository implements RunTransitionRepository {
    private final JdbcTemplate jdbc;

    public JdbcRunTransitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClaimedRun> claimBatch(String workerId, int batchSize) {
        return jdbc.query(
                "with locked_jobs as materialized ("
                        + "select j.id,j.concurrency_limit from job_definition j "
                        + "where exists (select 1 from job_run q where q.job_definition_id=j.id "
                        + "and q.status='QUEUED' and q.run_at<=now()) "
                        + "order by j.id for update skip locked), "
                        + "active as (select job_definition_id,count(*) n from job_run "
                        + "where status='RUNNING' group by job_definition_id), "
                        + "queued as (select r.id,r.job_definition_id,row_number() over "
                        + "(partition by r.job_definition_id order by r.run_at,r.id) n "
                        + "from job_run r join locked_jobs j on j.id=r.job_definition_id "
                        + "where r.status='QUEUED' and r.run_at<=now()), "
                        + "picked as (select q.id from queued q join locked_jobs j "
                        + "on j.id=q.job_definition_id left join active a "
                        + "on a.job_definition_id=q.job_definition_id "
                        + "where q.n<=j.concurrency_limit-coalesce(a.n,0) "
                        + "order by q.id limit ?) "
                        + "update job_run r set status='RUNNING',locked_by=?,lease_token=gen_random_uuid(),"
                        + "lease_until=now()+interval '15 seconds',started_at=now() "
                        + "from picked p,job_definition j where r.id=p.id and j.id=r.job_definition_id "
                        + "returning r.id,r.job_definition_id,r.idempotency_key,j.payload_json::text "
                        + "as payload_json,j.handler_type,j.timeout_sec,j.max_attempts,r.attempt,r.lease_token",
                (rs, rowNum) ->
                        new ClaimedRun(
                                rs.getObject("id", UUID.class),
                                rs.getObject("job_definition_id", UUID.class),
                                rs.getString("idempotency_key"),
                                rs.getString("payload_json"),
                                rs.getString("handler_type"),
                                rs.getInt("timeout_sec"),
                                rs.getInt("max_attempts"),
                                rs.getInt("attempt"),
                                rs.getObject("lease_token", UUID.class)),
                batchSize,
                workerId);
    }

    @Override
    public boolean heartbeat(UUID runId, String workerId, UUID leaseToken) {
        return jdbc.update(
                        "update job_run set lease_until=now()+interval '15 seconds' "
                                + "where id=? and status='RUNNING' and locked_by=? "
                                + "and lease_token=? and lease_until>now()",
                        runId,
                        workerId,
                        leaseToken)
                == 1;
    }

    @Override
    public boolean complete(UUID runId, String workerId, UUID leaseToken) {
        return jdbc.update(
                        "update job_run set status='SUCCEEDED',finished_at=now(),lease_until=null,"
                                + "locked_by=null,lease_token=null where id=? and status='RUNNING' "
                                + "and locked_by=? and lease_token=? and lease_until>now()",
                        runId,
                        workerId,
                        leaseToken)
                == 1;
    }

    @Override
    public boolean fail(
            UUID runId,
            String workerId,
            UUID leaseToken,
            RunStatus nextStatus,
            int nextAttempt,
            String error,
            double delaySeconds) {
        return jdbc.update(
                        "update job_run set status=?,attempt=?,error=?,"
                                + "run_at=now()+(? * interval '1 second'),lease_until=null,locked_by=null,"
                                + "lease_token=null,finished_at=case when ?='DEAD' then now() else null end "
                                + "where id=? and status='RUNNING' and locked_by=? and lease_token=? "
                                + "and lease_until>now()",
                        nextStatus.name(),
                        nextAttempt,
                        error,
                        delaySeconds,
                        nextStatus.name(),
                        runId,
                        workerId,
                        leaseToken)
                == 1;
    }

    @Override
    public List<ExpiredRun> findExpired(int batchSize) {
        return jdbc.query(
                "select r.id,r.locked_by,r.lease_token,r.attempt,j.max_attempts "
                        + "from job_run r join job_definition j on j.id=r.job_definition_id "
                        + "where r.status='RUNNING' and r.lease_until<now() "
                        + "order by r.lease_until limit ? for update of r skip locked",
                (rs, rowNum) ->
                        new ExpiredRun(
                                rs.getObject("id", UUID.class),
                                rs.getString("locked_by"),
                                rs.getObject("lease_token", UUID.class),
                                rs.getInt("attempt"),
                                rs.getInt("max_attempts")),
                batchSize);
    }

    @Override
    public boolean expire(ExpiredRun run, RunStatus nextStatus, int nextAttempt) {
        return jdbc.update(
                        "update job_run set status=?,attempt=?,locked_by=null,lease_until=null,"
                                + "lease_token=null,run_at=now(),error='worker lease expired',"
                                + "finished_at=case when ?='DEAD' then now() else null end "
                                + "where id=? and status='RUNNING' and locked_by=? and lease_token=? "
                                + "and lease_until<now()",
                        nextStatus.name(),
                        nextAttempt,
                        nextStatus.name(),
                        run.id(),
                        run.workerId(),
                        run.leaseToken())
                == 1;
    }

    @Override
    public boolean retry(UUID runId) {
        return jdbc.update(
                        "update job_run set status='QUEUED',run_at=now(),error=null "
                                + "where id=? and status in ('FAILED','DEAD')",
                        runId)
                == 1;
    }

    @Override
    public boolean cancel(UUID runId) {
        return jdbc.update(
                        "update job_run set status='CANCELLED',finished_at=now(),locked_by=null,"
                                + "lease_until=null,lease_token=null where id=? "
                                + "and status in ('QUEUED','RUNNING')",
                        runId)
                == 1;
    }

    @Override
    public List<UUID> insertFailedDependentSteps() {
        return jdbc.query(
                "insert into job_run(job_definition_id,workflow_run_id,step_key) "
                        + "select s.job_definition_id,wr.id,s.step_key from workflow_run wr "
                        + "join workflow_step s on s.workflow_id=wr.workflow_id "
                        + "where wr.status='RUNNING' "
                        + "and not exists(select 1 from job_run r where r.workflow_run_id=wr.id "
                        + "and r.step_key=s.step_key) "
                        + "and exists(select 1 from unnest(s.depends_on) dep where exists "
                        + "(select 1 from job_run p where p.workflow_run_id=wr.id "
                        + "and p.step_key=dep and p.status in ('FAILED','DEAD'))) returning id",
                (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    @Override
    public boolean skip(UUID runId) {
        return jdbc.update(
                        "update job_run set status='SKIPPED',finished_at=now(),"
                                + "error='dependency failed' where id=? and status='QUEUED'",
                        runId)
                == 1;
    }

    @Override
    public void addErrorLog(UUID runId, String message) {
        jdbc.update(
                "insert into run_log(run_id,level,message) values (?,'ERROR',?)", runId, message);
    }
}
