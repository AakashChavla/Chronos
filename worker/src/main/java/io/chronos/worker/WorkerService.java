package io.chronos.worker;

import io.chronos.common.RunContext;
import io.chronos.common.RunStateMachine;
import io.chronos.common.RunStatus;
import io.chronos.handlers.BuiltinHandlers;
import io.chronos.handlers.JobHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chronos.worker-enabled", havingValue = "true")
public class WorkerService {
  private final JdbcTemplate jdbc;
  private final String worker;
  private final Map<String, JobHandler> handlers;
  private final RunStateMachine states = new RunStateMachine();
  private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

  public WorkerService(
      JdbcTemplate jdbc, @Value("${chronos.worker-id}") String worker, BuiltinHandlers builtins) {
    this.jdbc = jdbc;
    this.worker = worker;
    this.handlers = new HashMap<>();
    builtins.all().forEach(h -> handlers.put(h.type(), h));
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() {
    jdbc.update(
        "insert into worker_node(id,heartbeat_at) values (?,now()) on conflict(id) do update set heartbeat_at=now()",
        worker);
    jdbc.update(
        "update job_run set lease_until=now()+interval '15 seconds' where status='RUNNING' and locked_by=?",
        worker);
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "with locked_jobs as materialized (select j.id,j.concurrency_limit from job_definition j where exists(select 1 from job_run q where q.job_definition_id=j.id and q.status='QUEUED' and q.run_at<=now()) order by j.id for update skip locked), active as (select job_definition_id,count(*) n from job_run where status='RUNNING' group by job_definition_id), queued as (select r.id,r.job_definition_id,row_number() over(partition by r.job_definition_id order by r.run_at,r.id) n from job_run r join locked_jobs j on j.id=r.job_definition_id where r.status='QUEUED' and r.run_at<=now()), picked as (select q.id from queued q join locked_jobs j on j.id=q.job_definition_id left join active a on a.job_definition_id=q.job_definition_id where q.n<=j.concurrency_limit-coalesce(a.n,0) order by q.id limit 10) update job_run r set status='RUNNING',locked_by=?,lease_until=now()+interval '15 seconds',started_at=now() from picked p,job_definition j where r.id=p.id and j.id=r.job_definition_id returning r.id,r.job_definition_id,r.idempotency_key,j.handler_type,j.payload_json,j.timeout_sec,j.max_attempts,r.attempt",
            worker);
    for (var r : rows) Thread.startVirtualThread(() -> execute(r));
  }

  private void execute(Map<String, Object> r) {
    UUID id = (UUID) r.get("id");
    ScheduledFuture<?> beat =
        heartbeats.scheduleAtFixedRate(
            () ->
                jdbc.update(
                    "update job_run set lease_until=now()+interval '15 seconds' where id=? and status='RUNNING' and locked_by=?",
                    id,
                    worker),
            5,
            5,
            TimeUnit.SECONDS);
    try {
      JobHandler h = handlers.get(r.get("handler_type"));
      if (h == null)
        throw new IllegalArgumentException("unknown handler: " + r.get("handler_type"));
      ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
      try {
        Future<?> task =
            executor.submit(
                () -> {
                  try {
                    h.handle(
                        new RunContext(
                            id,
                            (UUID) r.get("job_definition_id"),
                            String.valueOf(r.get("payload_json")),
                            String.valueOf(r.get("idempotency_key"))));
                  } catch (Exception e) {
                    throw new CompletionException(e);
                  }
                });
        task.get(((Number) r.get("timeout_sec")).longValue(), TimeUnit.SECONDS);
      } finally {
        executor.shutdownNow();
      }
      states.require(RunStatus.RUNNING, RunStatus.SUCCEEDED);
      jdbc.update(
          "update job_run set status='SUCCEEDED',finished_at=now(),lease_until=null,locked_by=null where id=? and status='RUNNING'",
          id);
    } catch (Exception e) {
      int attempt = ((Number) r.get("attempt")).intValue() + 1;
      int max = ((Number) r.get("max_attempts")).intValue();
      String status = attempt >= max ? "DEAD" : "QUEUED";
      states.require(RunStatus.RUNNING, status.equals("DEAD") ? RunStatus.DEAD : RunStatus.QUEUED);
      double delay =
          Math.min(300, Math.pow(2, attempt)) + ThreadLocalRandom.current().nextDouble(0, 1);
      jdbc.update(
          "update job_run set status=?,attempt=?,error=?,run_at=now()+(? * interval '1 second'),lease_until=null,locked_by=null,finished_at=case when ?='DEAD' then now() else null end where id=? and status='RUNNING'",
          status,
          attempt,
          Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()),
          delay,
          status,
          id);
      jdbc.update(
          "insert into run_log(run_id,level,message) values (?,'ERROR',?)",
          id,
          String.valueOf(e.getMessage()));
    } finally {
      beat.cancel(false);
    }
  }

  @Scheduled(fixedDelay = 5000)
  public void reap() {
    jdbc.update(
        "update job_run r set status=case when r.attempt+1>=j.max_attempts then 'DEAD' else 'QUEUED' end,attempt=r.attempt+1,locked_by=null,lease_until=null,run_at=now(),error='worker lease expired' from job_definition j where r.job_definition_id=j.id and r.status='RUNNING' and r.lease_until<now()");
  }
}
