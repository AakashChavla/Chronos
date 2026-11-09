package io.chronos.core.config;

import io.chronos.common.RunStateMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfiguration {
    @Bean
    public RunStateMachine runStateMachine() {
        return new RunStateMachine();
    }
}
