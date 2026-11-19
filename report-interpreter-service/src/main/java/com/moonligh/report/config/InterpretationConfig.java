package com.moonligh.report.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.report.domain.interpret.InterpretationOutputGuard;
import com.moonligh.report.domain.interpret.InterpretationValidator;
import com.moonligh.report.domain.kb.HybridRecall;
import com.moonligh.report.domain.kb.KbChunkStore;

@Configuration
public class InterpretationConfig {

    @Bean
    public Clock reportClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HybridRecall hybridRecall(KbChunkStore store, Clock reportClock) {
        return new HybridRecall(store, reportClock);
    }

    @Bean
    public InterpretationValidator interpretationValidator() {
        return new InterpretationValidator();
    }

    @Bean
    public InterpretationOutputGuard interpretationOutputGuard(InterpretationValidator validator) {
        return new InterpretationOutputGuard(validator);
    }
}
