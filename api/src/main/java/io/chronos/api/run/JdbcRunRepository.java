package io.chronos.api.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRunRepository implements RunRepository {
    private final JdbcTemplate jdbc;

    public JdbcRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RunResponse> findRecent(String status) {
        String sql =
                "select id,job_definition_id,status,attempt,run_at,locked_by,lease_until,lease_token,"
                        + "idempotency_key,error,started_at,finished_at,workflow_run_id,step_key,replay_of "
                        + "from job_run";
        if (status != null) {
            sql += " where status=?";
        }
        sql += " order by run_at desc limit 500";
        return status == null
                ? jdbc.query(sql, (rs, row) -> RunRowMapper.map(rs))
                : jdbc.query(sql, (rs, row) -> RunRowMapper.map(rs), status);
    }

    @Override
    public Optional<UUID> replayDead(UUID originalRunId) {
        List<UUID> ids =
                jdbc.query(
                        "insert into job_run(job_definition_id,replay_of,idempotency_key) "
                                + "select job_definition_id,id,gen_random_uuid()::text from job_run "
                                + "where id=? and status='DEAD' returning id",
                        (rs, row) -> rs.getObject("id", UUID.class),
                        originalRunId);
        return ids.stream().findFirst();
    }
}
