package io.chronos.core.job.repository;

import io.chronos.core.job.domain.JobDefinition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcJobRepository implements JobRepository {
    private static final String SELECT_COLUMNS =
            "id,name,handler_type,payload_json::text as payload_json,schedule_cron,timezone,"
                    + "schedule_next_at,max_attempts,timeout_sec,concurrency_limit,enabled,created_at";

    private final JdbcTemplate jdbc;

    public JdbcJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<JobDefinition> findAll() {
        return jdbc.query(
                "select " + SELECT_COLUMNS + " from job_definition order by name", this::mapRow);
    }

    @Override
    public Optional<JobDefinition> findById(UUID id) {
        List<JobDefinition> result =
                jdbc.query(
                        "select " + SELECT_COLUMNS + " from job_definition where id=?",
                        this::mapRow,
                        id);
        return result.stream().findFirst();
    }

    @Override
    public JobDefinition insert(JobDefinition job) {
        jdbc.update(
                "insert into job_definition(id,name,handler_type,payload_json,schedule_cron,timezone,"
                        + "max_attempts,timeout_sec,concurrency_limit,enabled) "
                        + "values(?,?,?,cast(? as jsonb),?,?,?,?,?,?)",
                job.id(),
                job.name(),
                job.handlerType(),
                job.payloadJson(),
                job.scheduleCron(),
                job.timezone(),
                job.maxAttempts(),
                job.timeoutSec(),
                job.concurrencyLimit(),
                job.enabled());
        return findById(job.id()).orElseThrow();
    }

    @Override
    public boolean update(JobDefinition job) {
        int changed =
                jdbc.update(
                        "update job_definition set name=?,handler_type=?,payload_json=cast(? as jsonb),"
                                + "schedule_cron=?,timezone=?,max_attempts=?,timeout_sec=?,"
                                + "concurrency_limit=?,enabled=? where id=?",
                        job.name(),
                        job.handlerType(),
                        job.payloadJson(),
                        job.scheduleCron(),
                        job.timezone(),
                        job.maxAttempts(),
                        job.timeoutSec(),
                        job.concurrencyLimit(),
                        job.enabled(),
                        job.id());
        return changed == 1;
    }

    private JobDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new JobDefinition(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("handler_type"),
                rs.getString("payload_json"),
                rs.getString("schedule_cron"),
                rs.getString("timezone"),
                rs.getObject("schedule_next_at", java.time.OffsetDateTime.class),
                rs.getInt("max_attempts"),
                rs.getInt("timeout_sec"),
                rs.getInt("concurrency_limit"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
