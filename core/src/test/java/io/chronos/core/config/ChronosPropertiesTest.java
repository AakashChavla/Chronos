package io.chronos.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ChronosPropertiesTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(CoreConfiguration.class);

    @Test
    void bindsValidatedWorkerAndSchedulerDefaults() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    ChronosProperties properties = context.getBean(ChronosProperties.class);
                    assertThat(properties.worker().id()).isEqualTo("local");
                    assertThat(properties.worker().pollIntervalMs()).isEqualTo(1000);
                    assertThat(properties.worker().leaseDurationMs()).isEqualTo(30000);
                    assertThat(properties.worker().batchSize()).isEqualTo(10);
                    assertThat(properties.worker().maxConcurrency()).isEqualTo(20);
                    assertThat(properties.worker().shutdownGraceMs()).isEqualTo(30000);
                    assertThat(properties.worker().heartbeatIntervalMs()).isEqualTo(10000);
                    assertThat(properties.scheduler().pollIntervalMs()).isEqualTo(1000);
                });
    }

    @Test
    void rejectsInvalidWorkerBatchSizeAtStartup() {
        contextRunner
                .withPropertyValues("chronos.worker.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsBlankWorkerIdentityAtStartup() {
        contextRunner
                .withPropertyValues("chronos.worker.id= ")
                .run(context -> assertThat(context).hasFailed());
    }
}
