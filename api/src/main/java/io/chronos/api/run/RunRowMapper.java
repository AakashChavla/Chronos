package io.chronos.api.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

final class RunRowMapper {
    private RunRowMapper() {}

    static RunResponse map(ResultSet rs) throws SQLException {
        return new RunResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("job_definition_id", UUID.class),
                rs.getString("status"),
                rs.getInt("attempt"),
                rs.getObject("run_at", OffsetDateTime.class),
                rs.getString("locked_by"),
                rs.getObject("lease_until", OffsetDateTime.class),
                rs.getObject("lease_token", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("error"),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class),
                rs.getObject("workflow_run_id", UUID.class),
                rs.getString("step_key"),
                rs.getObject("replay_of", UUID.class));
    }
}
