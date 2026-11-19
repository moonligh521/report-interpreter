package com.moonligh.report.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.moonligh.report.domain.parse.ParserRegistry;
import com.moonligh.report.domain.parse.ReportParser;

@Configuration
public class ParserConfig {

    @Bean
    public ParserRegistry parserRegistry(List<ReportParser> parsers) {
        return new ParserRegistry(parsers);
    }
}