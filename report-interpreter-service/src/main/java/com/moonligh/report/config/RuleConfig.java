package com.moonligh.report.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.moonligh.report.domain.critical.CriticalEventStore;
import com.moonligh.report.domain.critical.CriticalNotificationManager;
import com.moonligh.report.domain.rule.ClinicalCatalog;
import com.moonligh.report.domain.rule.ClinicalCatalogPort;
import com.moonligh.report.domain.rule.CriticalCoverageChecker;
import com.moonligh.report.domain.rule.CriticalValueEngine;
import com.moonligh.report.domain.rule.ReferenceRangeEngine;
import com.moonligh.report.domain.rule.ReportIdentityConsistencyChecker;
import com.moonligh.report.domain.rule.RuleContextResolver;
import com.moonligh.report.domain.rule.SafetyGate;
import com.moonligh.report.infrastructure.catalog.FakeClinicalCatalogAdapter;

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
    public FakeClinicalCatalogAdapter fakeClinicalCatalogAdapter() {
        return new FakeClinicalCatalogAdapter();
    }

    @Bean
    @Primary
    public ClinicalCatalogPort clinicalCatalogPort(
            ReportProperties properties, FakeClinicalCatalogAdapter fake) {
        String adapter = properties.getClinicalFacts().getCatalog().getAdapter();
        if (!"fake".equalsIgnoreCase(adapter)) {
            throw new IllegalStateException("clinical catalog adapter not supported yet: " + adapter);
        }
        return fake;
    }

    @Bean
    public ClinicalCatalog activeClinicalCatalog(ClinicalCatalogPort port, ReportProperties properties) {
        return port.load(properties.getClinicalFacts().getCatalog().getActiveVersion());
    }

    @Bean
    public ReferenceRangeEngine referenceRangeEngine(ClinicalCatalog catalog) {
        return new ReferenceRangeEngine(catalog.referenceRules());
    }

    @Bean
    public CriticalValueEngine criticalValueEngine(ClinicalCatalog catalog) {
        return new CriticalValueEngine(catalog.criticalRuleTable());
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