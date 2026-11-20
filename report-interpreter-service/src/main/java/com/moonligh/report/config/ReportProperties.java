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

    private final Archive archive = new Archive();

    private final ClinicalFacts clinicalFacts = new ClinicalFacts();

    public Interpretation getInterpretation() {
        return interpretation;
    }

    public Archive getArchive() {
        return archive;
    }

    public ClinicalFacts getClinicalFacts() {
        return clinicalFacts;
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

        private final Probe probe = new Probe();

        private final Extraction extraction = new Extraction();

        private final Limits limits = new Limits();

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

        public Probe getProbe() {
            return probe;
        }

        public Extraction getExtraction() {
            return extraction;
        }

        public Limits getLimits() {
            return limits;
        }
    }

    /** report.parse.probe.*：文本层路由阈值。 */
    public static class Probe {

        private double minNonEmptyPageRatio = 0.80;
        private double minExtractableCharRatio = 0.85;
        private double minAverageCharsPerPage = 20.0;
        private int minTotalChars = 30;

        public double getMinNonEmptyPageRatio() {
            return minNonEmptyPageRatio;
        }

        public void setMinNonEmptyPageRatio(double minNonEmptyPageRatio) {
            this.minNonEmptyPageRatio = minNonEmptyPageRatio;
        }

        public double getMinExtractableCharRatio() {
            return minExtractableCharRatio;
        }

        public void setMinExtractableCharRatio(double minExtractableCharRatio) {
            this.minExtractableCharRatio = minExtractableCharRatio;
        }

        public double getMinAverageCharsPerPage() {
            return minAverageCharsPerPage;
        }

        public void setMinAverageCharsPerPage(double minAverageCharsPerPage) {
            this.minAverageCharsPerPage = minAverageCharsPerPage;
        }

        public int getMinTotalChars() {
            return minTotalChars;
        }

        public void setMinTotalChars(int minTotalChars) {
            this.minTotalChars = minTotalChars;
        }
    }

    /** report.parse.extraction.*：外置抽取适配。 */
    public static class Extraction {

        /** fake | http */
        private String mode = "fake";

        private String baseUrl = "http://127.0.0.1:18093";

        private long connectTimeoutMs = 3_000;

        private long readTimeoutMs = 30_000;

        private long maxRequestBytes = 20L * 1024 * 1024;

        private long maxResponseBytes = 20L * 1024 * 1024;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public long getMaxRequestBytes() {
            return maxRequestBytes;
        }

        public void setMaxRequestBytes(long maxRequestBytes) {
            this.maxRequestBytes = maxRequestBytes;
        }

        public long getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(long maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }
    }

    /** report.parse.limits.*：页数/像素等资源限额。 */
    public static class Limits {

        private int maxPdfPages = 50;

        private int maxImagePixels = 40_000_000;

        public int getMaxPdfPages() {
            return maxPdfPages;
        }

        public void setMaxPdfPages(int maxPdfPages) {
            this.maxPdfPages = maxPdfPages;
        }

        public int getMaxImagePixels() {
            return maxImagePixels;
        }

        public void setMaxImagePixels(int maxImagePixels) {
            this.maxImagePixels = maxImagePixels;
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

    /** report.archive.*：档案 Outbox（与危急通知 Outbox 分离）。 */
    public static class Archive {

        /** 单条档案最大投递次数，用尽后进入 DEAD，只能经补偿接口拉回。 */
        private int maxAttempts = 5;

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

    /** report.clinical-facts.*：人口学与临床目录。 */
    public static class ClinicalFacts {

        /** fake | http-identity */
        private String patientFactsAdapter = "fake";

        private final Identity identity = new Identity();

        private final Catalog catalog = new Catalog();

        public String getPatientFactsAdapter() {
            return patientFactsAdapter;
        }

        public void setPatientFactsAdapter(String patientFactsAdapter) {
            this.patientFactsAdapter = patientFactsAdapter;
        }

        public Identity getIdentity() {
            return identity;
        }

        public Catalog getCatalog() {
            return catalog;
        }
    }

    /** report.clinical-facts.identity.* */
    public static class Identity {

        private String baseUrl = "";

        private long connectTimeoutMs = 1_000;

        private long readTimeoutMs = 2_000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /** report.clinical-facts.catalog.* */
    public static class Catalog {

        /** fake | classpath | db */
        private String adapter = "fake";

        private String activeVersion = "builtin-v1";

        public String getAdapter() {
            return adapter;
        }

        public void setAdapter(String adapter) {
            this.adapter = adapter;
        }

        public String getActiveVersion() {
            return activeVersion;
        }

        public void setActiveVersion(String activeVersion) {
            this.activeVersion = activeVersion;
        }
    }
}