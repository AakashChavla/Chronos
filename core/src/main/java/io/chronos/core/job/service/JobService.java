package io.chronos.core.job.service;

import io.chronos.core.job.domain.JobDefinition;
import io.chronos.core.job.repository.JobRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class JobService {
  private final JobRepository jobs;

  public JobService(JobRepository jobs) {
    this.jobs = jobs;
  }

  public List<JobDefinition> list() {
    return jobs.findAll();
  }

  public Optional<JobDefinition> find(UUID id) {
    return jobs.findById(id);
  }

  public JobDefinition create(CreateJobCommand command) {
    return jobs.insert(
        new JobDefinition(
            UUID.randomUUID(),
            command.name(),
            command.handlerType(),
            Optional.ofNullable(command.payloadJson()).orElse("{}"),
            command.scheduleCron(),
            Optional.ofNullable(command.timezone()).orElse("UTC"),
            null,
            Optional.ofNullable(command.maxAttempts()).orElse(3),
            Optional.ofNullable(command.timeoutSec()).orElse(60),
            Optional.ofNullable(command.concurrencyLimit()).orElse(1),
            Optional.ofNullable(command.enabled()).orElse(true),
            null));
  }

  public boolean update(UUID id, CreateJobCommand command) {
    JobDefinition current = jobs.findById(id).orElse(null);
    if (current == null) {
      return false;
    }
    JobDefinition updated =
        new JobDefinition(
            id,
            Optional.ofNullable(command.name()).orElse(current.name()),
            Optional.ofNullable(command.handlerType()).orElse(current.handlerType()),
            Optional.ofNullable(command.payloadJson()).orElse(current.payloadJson()),
            Optional.ofNullable(command.scheduleCron()).orElse(current.scheduleCron()),
            Optional.ofNullable(command.timezone()).orElse(current.timezone()),
            current.scheduleNextAt(),
            Optional.ofNullable(command.maxAttempts()).orElse(current.maxAttempts()),
            Optional.ofNullable(command.timeoutSec()).orElse(current.timeoutSec()),
            Optional.ofNullable(command.concurrencyLimit()).orElse(current.concurrencyLimit()),
            Optional.ofNullable(command.enabled()).orElse(current.enabled()),
            current.createdAt());
    return jobs.update(updated);
  }

  public record CreateJobCommand(
      String name,
      String handlerType,
      String payloadJson,
      String scheduleCron,
      String timezone,
      Integer maxAttempts,
      Integer timeoutSec,
      Integer concurrencyLimit,
      Boolean enabled) {}
}
