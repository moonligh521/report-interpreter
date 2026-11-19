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

    private final Interpretation interpretation = new Interpretation();

    public Interpretation getInterpretation() {
        return interpretation;
    }

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

    /** report.interpretation.*：解读与 KB 依赖策略。 */
    public static class Interpretation {

        /**
         * 知识库无可引用依据（为空或不可用）时是否允许降级解读（仅复述报告数据与规则判定）。
         * 为 false 时 fail-closed，返回 4104。
         */
        private boolean allowDegraded = true;

        /** 召回的结构化过滤所用专科（LAB_PANEL 对应）。 */
        private String labSpecialty = "LAB";

        /** Guard 对 RETRYABLE 的最大生成次数（含首次）。 */
        private int maxAttempts = 2;

        public boolean isAllowDegraded() {
            return allowDegraded;
        }

        public void setAllowDegraded(boolean allowDegraded) {
            this.allowDegraded = allowDegraded;
        }

        public String getLabSpecialty() {
            return labSpecialty;
        }

        public void setLabSpecialty(String labSpecialty) {
            this.labSpecialty = labSpecialty;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
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