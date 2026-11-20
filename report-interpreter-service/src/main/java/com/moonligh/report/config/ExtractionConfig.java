package com.moonligh.report.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;
import com.moonligh.report.infrastructure.extraction.HttpDocumentExtractionAdapter;

/**
 * 抽取端口装配：默认 Fake；mode=http 时切换 Http 适配器。
 */
@Configuration
public class ExtractionConfig {

    @Bean
    public FakeDocumentExtractionAdapter fakeDocumentExtractionAdapter() {
        return new FakeDocumentExtractionAdapter();
    }

    @Bean
    public HttpClient extractionHttpClient(ReportProperties properties) {
        ReportProperties.Extraction e = properties.getParse().getExtraction();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(e.getConnectTimeoutMs()))
                .build();
    }

    @Bean
    public HttpDocumentExtractionAdapter httpDocumentExtractionAdapter(
            HttpClient extractionHttpClient, ReportProperties properties) {
        return new HttpDocumentExtractionAdapter(extractionHttpClient, properties);
    }

    @Bean
    @Primary
    public DocumentExtractionPort documentExtractionPort(
            ReportProperties properties,
            FakeDocumentExtractionAdapter fake,
            HttpDocumentExtractionAdapter http) {
        String mode = properties.getParse().getExtraction().getMode();
        if ("http".equalsIgnoreCase(mode)) {
            return http;
        }
        return fake;
    }
}
