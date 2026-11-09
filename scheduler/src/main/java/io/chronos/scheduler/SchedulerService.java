package io.chronos.scheduler;

import io.chronos.core.run.service.RunTransitionService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "chronos.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulerService {
    private final JdbcTemplate jdbc;
    private final RunTransitionService transitions;

    public SchedulerService(JdbcTemplate jdbc, RunTransitionService transitions) {
        this.jdbc = jdbc;
        this.transitions = transitions;
    }

    @Transactional
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject("select pg_try_advisory_xact_lock(987654321)", Boolean.class)))
            return;
        List<Map<String, Object>> due =
                jdbc.queryForList(
                        "select id,schedule_cron,timezone,schedule_next_at,now() as db_now from job_definition where enabled and schedule_cron is not null and (schedule_next_at is null or schedule_next_at<=now()) for update");
        for (Map<String, Object> job : due) {
            UUID id = (UUID) job.get("id");
            jdbc.update(
                    "insert into job_run(job_definition_id,run_at) values (?,?)",
                    id,
                    job.get("schedule_next_at") == null
                            ? job.get("db_now")
                            : job.get("schedule_next_at"));
            ZonedDateTime base =
                    ((OffsetDateTime) job.get("db_now"))
                            .atZoneSameInstant(ZoneId.of(String.valueOf(job.get("timezone"))));
            ZonedDateTime next =
                    CronExpression.parse(String.valueOf(job.get("schedule_cron"))).next(base);
            if (next != null)
                jdbc.update(
                        "update job_definition set schedule_next_at=? where id=?",
                        next.toOffsetDateTime(),
                        id);
        }
        transitions.skipFailedWorkflowSteps();
        jdbc.update(
                "insert into job_run(job_definition_id,workflow_run_id,step_key) select s.job_definition_id,wr.id,s.step_key from workflow_run wr join workflow_step s on s.workflow_id=wr.workflow_id where wr.status='RUNNING' and not exists(select 1 from job_run r where r.workflow_run_id=wr.id and r.step_key=s.step_key) and not exists(select 1 from unnest(s.depends_on) dep where not exists(select 1 from job_run p where p.workflow_run_id=wr.id and p.step_key=dep and p.status='SUCCEEDED'))");
        jdbc.update(
                "update workflow_run w set status='FAILED',finished_at=now() where w.status='RUNNING' and exists(select 1 from job_run r where r.workflow_run_id=w.id and r.status in ('FAILED','DEAD'))");
        jdbc.update(
                "update workflow_run wr set status='SUCCEEDED',finished_at=now() where wr.status='RUNNING' and not exists(select 1 from workflow_step s where s.workflow_id=wr.workflow_id and not exists(select 1 from job_run r where r.workflow_run_id=wr.id and r.step_key=s.step_key and r.status='SUCCEEDED'))");
    }
}
