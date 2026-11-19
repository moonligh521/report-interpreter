package com.moonligh.report.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.rule.CriticalCoverageChecker;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.ReferenceRangeEngine;
import com.moonligh.report.domain.rule.ReportIdentityConsistencyChecker;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.domain.rule.SafetyGate;

/**
 * 规则域对象装配：domain 类保持无 Spring 注解，由此处注册为 Bean。
 */
@Configuration
public class RuleConfig {

    @Bean
    public ReportIdentityConsistencyChecker reportIdentityConsistencyChecker() {
        return new ReportIdentityConsistencyChecker();
    }

    @Bean
    public CriticalCoverageChecker criticalCoverageChecker() {
        return new CriticalCoverageChecker();
    }

    @Bean
    public RuleContextResolver ruleContextResolver() {
        return new RuleContextResolver();
    }

    @Bean
    public ReferenceRangeEngine referenceRangeEngine() {
        return new ReferenceRangeEngine();
    }

    @Bean
    public CriticalValueEngine criticalValueEngine() {
        return new CriticalValueEngine(CriticalRuleTable.builtIn());
    }

    @Bean
    public SafetyGate safetyGate() {
        return new SafetyGate();
    }

    @Bean
    public CriticalNotificationManager criticalNotificationManager(
            CriticalEventStore store, ReportProperties properties) {
        return new CriticalNotificationManager(store, properties.getCritical().getMaxNotifyAttempts());
    }
}