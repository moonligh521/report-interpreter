package com.moonligh.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 报告解读业务配置根：report.*
 */
@ConfigurationProperties(prefix = "report")
public class ReportProperties {

    /** 服务对外说明，仅占位。 */
    private String displayName = "report-interpreter";

    private final Parse parse = new Parse();

    private final Critical critical = new Critical();

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Parse getParse() {
        return parse;
    }

    public Critical getCritical() {
        return critical;
    }

    /** report.parse.*：上传与解析限制。 */
    public static class Parse {

        /** 单文件最大字节数，默认 20MB。 */
        private long maxFileBytes = 20L * 1024 * 1024;

        /** 是否允许 text/plain 与 .lab.txt 金标夹具；生产必须为 false。 */
        private boolean allowTextFixture = false;

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public boolean isAllowTextFixture() {
            return allowTextFixture;
        }

        public void setAllowTextFixture(boolean allowTextFixture) {
            this.allowTextFixture = allowTextFixture;
        }
    }

    /** report.critical.*：危急通知。 */
    public static class Critical {

        /** 单条危急通知最大投递次数，超过后升级为 ESCALATED（人工）。 */
        private int maxNotifyAttempts = 5;

        public int getMaxNotifyAttempts() {
            return maxNotifyAttempts;
        }

        public void setMaxNotifyAttempts(int maxNotifyAttempts) {
            this.maxNotifyAttempts = maxNotifyAttempts;
        }
    }
}