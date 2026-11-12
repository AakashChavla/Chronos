package io.chronos.core.config;

import io.chronos.common.RunStateMachine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChronosProperties.class)
public class CoreConfiguration {
    @Bean
    public RunStateMachine runStateMachine() {
        return new RunStateMachine();
    }
}
