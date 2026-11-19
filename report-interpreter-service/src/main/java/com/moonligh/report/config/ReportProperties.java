package com.moonligh.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 报告解读业务配置根：report.*
 */
@ConfigurationProperties(prefix = "report")
public class ReportProperties {

    /** 服务对外说明，仅占位。 */
    private String displayName = "report-interpreter";

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
