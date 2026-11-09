package io.chronos.core.job.repository;

import io.chronos.core.job.domain.JobDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {
  List<JobDefinition> findAll();

  Optional<JobDefinition> findById(UUID id);

  JobDefinition insert(JobDefinition job);

  boolean update(JobDefinition job);
}
