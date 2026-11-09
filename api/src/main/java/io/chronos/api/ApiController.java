package io.chronos.api;

import io.chronos.core.run.service.RunTransitionService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final JdbcTemplate jdbc;
  private final RunTransitionService transitions;

  public ApiController(JdbcTemplate jdbc, RunTransitionService transitions) {
    this.jdbc = jdbc;
    this.transitions = transitions;
  }

  public record Step(String stepKey, UUID jobDefinitionId, List<String> dependsOn) {}

  public record WorkflowRequest(String name, List<Step> steps) {}

  @PostMapping("/jobs/{id}/trigger")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> trigger(@PathVariable("id") UUID id) {
    UUID run = UUID.randomUUID();
    jdbc.update("insert into job_run(id,job_definition_id) values(?,?)", run, id);
    return Map.of("runId", run);
  }

  @PostMapping("/workflows")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> workflow(@RequestBody WorkflowRequest w) {
    validate(w.steps());
    UUID id = UUID.randomUUID();
    jdbc.update("insert into workflow(id,name) values(?,?)", id, w.name());
    for (Step s : w.steps())
      jdbc.update(
          "insert into workflow_step(workflow_id,step_key,job_definition_id,depends_on) values(?,?,?,?::text[])",
          id,
          s.stepKey(),
          s.jobDefinitionId(),
          "{" + String.join(",", Optional.ofNullable(s.dependsOn()).orElse(List.of())) + "}");
    return Map.of("id", id);
  }

  private void validate(List<Step> ss) {
    Set<String> keys = new HashSet<>();
    for (Step s : ss)
      if (!keys.add(s.stepKey())) throw new Unprocessable("duplicate step " + s.stepKey());
    for (Step s : ss)
      for (String d : Optional.ofNullable(s.dependsOn()).orElse(List.of()))
        if (!keys.contains(d))
          throw new Unprocessable("unknown dependency " + d + " on step " + s.stepKey());
    Map<String, Integer> c = new HashMap<>();
    for (Step s : ss) visit(s.stepKey(), ss, c);
  }

  private void visit(String k, List<Step> s, Map<String, Integer> c) {
    if (c.getOrDefault(k, 0) == 1) throw new Unprocessable("cycle at step " + k);
    if (c.getOrDefault(k, 0) == 2) return;
    c.put(k, 1);
    s.stream()
        .filter(x -> x.stepKey().equals(k))
        .findFirst()
        .orElseThrow()
        .dependsOn()
        .forEach(d -> visit(d, s, c));
    c.put(k, 2);
  }

  @PostMapping("/workflows/{id}/trigger")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> triggerWorkflow(@PathVariable("id") UUID id) {
    UUID run = UUID.randomUUID();
    jdbc.update("insert into workflow_run(id,workflow_id) values(?,?)", run, id);
    jdbc.update(
        "insert into job_run(job_definition_id,workflow_run_id,step_key) select job_definition_id,?,step_key from workflow_step where workflow_id=? and cardinality(depends_on)=0",
        run,
        id);
    return Map.of("workflowRunId", run);
  }

  @GetMapping("/runs")
  public List<Map<String, Object>> runs(
      @RequestParam(name = "status", required = false) String status) {
    return status == null
        ? jdbc.queryForList("select * from job_run order by run_at desc limit 500")
        : jdbc.queryForList(
            "select * from job_run where status=? order by run_at desc limit 500", status);
  }

  @PostMapping("/runs/{id}/retry")
  public void retry(@PathVariable("id") UUID id) {
    transitions.retry(id);
  }

  @PostMapping("/runs/{id}/cancel")
  public void cancel(@PathVariable("id") UUID id) {
    transitions.cancel(id);
  }

  @PostMapping("/runs/{id}/replay")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> replay(@PathVariable("id") UUID id) {
    UUID n = UUID.randomUUID();
    jdbc.update(
        "insert into job_run(id,job_definition_id,replay_of,idempotency_key) select ?,job_definition_id,id,gen_random_uuid()::text from job_run where id=? and status='DEAD'",
        n,
        id);
    return Map.of("runId", n, "replayOf", id);
  }

  @GetMapping("/workers")
  public List<Map<String, Object>> workers() {
    return jdbc.queryForList(
        "select w.*,count(r.id) current_running from worker_node w left join job_run r on r.locked_by=w.id and r.status='RUNNING' group by w.id,w.heartbeat_at,w.current_runs order by heartbeat_at desc");
  }

  @GetMapping(value = "/stream/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() {
    SseEmitter e = new SseEmitter(0L);
    java.util.concurrent.ScheduledExecutorService updates =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    e.onCompletion(updates::shutdown);
    e.onTimeout(updates::shutdown);
    e.onError(x -> updates.shutdown());
    updates.scheduleAtFixedRate(
        () -> {
          try {
            e.send(SseEmitter.event().name("runs").data(runs(null)));
          } catch (Exception x) {
            e.completeWithError(x);
            updates.shutdown();
          }
        },
        0,
        2,
        java.util.concurrent.TimeUnit.SECONDS);
    return e;
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  static class Unprocessable extends RuntimeException {
    Unprocessable(String m) {
      super(m);
    }
  }

  @ExceptionHandler(Unprocessable.class)
  ProblemDetail problem(Unprocessable e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }
}
