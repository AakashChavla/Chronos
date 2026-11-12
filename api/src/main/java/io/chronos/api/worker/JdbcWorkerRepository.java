package io.chronos.api.worker;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerRepository implements WorkerRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WorkerResponse> findAll() {
        return jdbc.query(
                "select w.id,w.heartbeat_at,w.current_runs,count(r.id) current_running "
                        + "from worker_node w left join job_run r on r.locked_by=w.id "
                        + "and r.status='RUNNING' group by w.id,w.heartbeat_at,w.current_runs "
                        + "order by w.heartbeat_at desc",
                (rs, row) ->
                        new WorkerResponse(
                                rs.getString("id"),
                                rs.getObject("heartbeat_at", OffsetDateTime.class),
                                rs.getInt("current_runs"),
                                rs.getLong("current_running")));
    }
}
