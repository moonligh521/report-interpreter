package com.moonligh.report.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.moonligh.report.domain.rule.PatientFactsPort;
import com.moonligh.report.infrastructure.facts.FakePatientFactsAdapter;
import com.moonligh.report.infrastructure.facts.HttpIdentityPatientFactsAdapter;

/**
 * clinical-facts 人口学端口装配：默认 Fake；可切 http-identity。
 */
@Configuration
public class ClinicalFactsConfig {

    @Bean
    public FakePatientFactsAdapter fakePatientFactsAdapter() {
        return new FakePatientFactsAdapter();
    }

    @Bean
    public HttpClient identityHttpClient(ReportProperties properties) {
        ReportProperties.Identity identity = properties.getClinicalFacts().getIdentity();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(identity.getConnectTimeoutMs()))
                .build();
    }

    @Bean
    public HttpIdentityPatientFactsAdapter httpIdentityPatientFactsAdapter(
            HttpClient identityHttpClient, ReportProperties properties) {
        return new HttpIdentityPatientFactsAdapter(identityHttpClient, properties);
    }

    @Bean
    @Primary
    public PatientFactsPort patientFactsPort(
            ReportProperties properties,
            FakePatientFactsAdapter fake,
            HttpIdentityPatientFactsAdapter http) {
        String adapter = properties.getClinicalFacts().getPatientFactsAdapter();
        if ("http-identity".equalsIgnoreCase(adapter)) {
            String base = properties.getClinicalFacts().getIdentity().getBaseUrl();
            if (base == null || base.isBlank()) {
                throw new IllegalStateException("report.clinical-facts.identity.base-url is required for http-identity");
            }
            return http;
        }
        return fake;
    }
}
