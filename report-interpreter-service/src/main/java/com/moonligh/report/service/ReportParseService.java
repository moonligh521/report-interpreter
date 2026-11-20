package com.moonligh.report.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.CreateReportRes;
import com.moonligh.report.api.res.CriticalEventsRes;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.IndicatorDto;
import com.moonligh.report.api.res.ReportDetailRes;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.parse.DocumentQualityStatus;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseQualityGate;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ParserRegistry;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ProbeReport;
import com.moonligh.report.domain.parse.QualityVerdict;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.domain.parse.UploadGuard;
import com.moonligh.report.domain.rule.IdentifiedIndicator;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.domain.rule.SafetyVerdict;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;
import com.moonligh.report.infrastructure.parser.NativePdfParser;
import com.moonligh.report.infrastructure.parser.OcrLayoutParser;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportSourceEvidenceRow;

/**
 * 报告建单、上传校验、解析与版本化落库（仅 LAB_PANEL）。
 */
@Service
public class ReportParseService {

    static final String STATUS_CREATED = "CREATED";
    static final String STATUS_PARSED = "PARSED";
    static final String STATUS_NEED_MANUAL_REVIEW = "NEED_MANUAL_REVIEW";
    private static final String UNKNOWN_TYPE = "UNKNOWN";
    private static final int MAX_NAME_LENGTH = 128;

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final ParserRegistry registry;
    private final ReportProperties properties;
    private final ReportRuleService rules;
    private final IndicatorDtoAssembler assembler;
    private final ParseManifestRecorder manifests;
    private final ObjectMapper json = new ObjectMapper();

    public ReportParseService(
            ReportJobStore jobs, ReportParseStore parses, ParserRegistry registry, ReportProperties properties,
            ReportRuleService rules, IndicatorDtoAssembler assembler, ParseManifestRecorder manifests) {
        this.assembler = assembler;
        this.jobs = jobs;
        this.parses = parses;
        this.registry = registry;
        this.properties = properties;
        this.rules = rules;
        this.manifests = manifests;
    }

    @Transactional
    public CreateReportRes create(
            String accountId, String patientId, String idempotencyKey, String reportType, UploadedFile file,
            String expectedPatientName) {
        if (!StringUtils.hasText(patientId)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "patientId 不能为空");
        }
        ReportType type = resolveType(reportType);
        UploadGuard.Kind kind = checkFile(file);
        String expectedName = normalizeExpectedName(expectedPatientName);
        String hash = requestHash(patientId, type, expectedName, file.content());

        Optional<ReportJobRow> existing = jobs.findByIdempotency(accountId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        String reportId = "rpt_" + UUID.randomUUID().toString().replace("-", "");
        String sourceText = kind == UploadGuard.Kind.TEXT_FIXTURE
                ? new String(file.content(), StandardCharsets.UTF_8)
                : null;
        ReportJobRow row = new ReportJobRow(
                reportId, accountId, patientId, idempotencyKey, STATUS_CREATED, 1L,
                type.name(), hash, sourceText, expectedName, null, null);
        try {
            jobs.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发同键：以先入库者为准回放/冲突。
            return replay(jobs.findByIdempotency(accountId, idempotencyKey).orElseThrow(() -> e), hash);
        }
        ParseContext ctx = new ParseContext(reportId, type, file.fileName(), file.contentType(), file.content());
        Optional<ReportParser> parser = registry.find(ctx);
        if (parser.isEmpty()) {
            // PDF/图片不得静默停在 CREATED。
            if (kind == UploadGuard.Kind.PDF || kind == UploadGuard.Kind.IMAGE) {
                manifests.recordFailureNewTx(manifests.row(
                        reportId, hash, failureManifestVersion(), "NONE", "0", "", "", "", 0, 0, 0,
                        DocumentQualityStatus.FAILED, List.of(), null, List.of(),
                        "ROUTE", "NO_PARSER"));
                throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无可用解析器");
            }
            return new CreateReportRes(reportId, STATUS_CREATED, 0);
        }
        ParseOutcome outcome = runParse(reportId, ctx, parser.get(), null, expectedName, hash);
        return new CreateReportRes(reportId, outcome.status(), outcome.version());
    }

    @Transactional
    public CreateReportRes reparse(String accountId, String reportId, String idempotencyKey) {
        ReportJobRow job = jobs.lockById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        Optional<ReportParseResultRow> replayed = parses.findByRequestKey(reportId, idempotencyKey);
        if (replayed.isPresent()) {
            return new CreateReportRes(reportId, job.status(), replayed.get().parseVersion());
        }
        ReportType type = resolveType(job.reportType());
        if (!StringUtils.hasText(job.sourceText())) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无可重解析的原文");
        }
        // 新一代：旧 epoch 的在途事件写入此后会被拒绝（job 行已被 lockById 锁住）。
        jobs.bumpEpoch(reportId);
        ParseContext ctx = new ParseContext(
                reportId, type, "reparse.lab.txt", "text/plain", job.sourceText().getBytes(StandardCharsets.UTF_8));
        ReportParser parser = registry.find(ctx)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无可用解析器"));
        String hash = job.requestHash() == null ? "reparse" : job.requestHash();
        ParseOutcome outcome = runParse(reportId, ctx, parser, idempotencyKey, job.expectedPatientName(), hash);
        return new CreateReportRes(reportId, outcome.status(), outcome.version());
    }

    @Transactional(readOnly = true)
    public ReportDetailRes get(String accountId, String reportId) {
        ReportJobRow job = jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        String jobType = job.reportType() == null ? UNKNOWN_TYPE : job.reportType();
        Optional<ReportParseResultRow> active = parses.findActive(reportId);
        if (active.isEmpty()) {
            return emptyDetail(reportId, jobType, job.status());
        }
        ReportParseResultRow parse = active.get();
        StructureJson.StructurePayload structure = StructureJson.read(parse.structureJson());
        Optional<ReportRuleService.RuleView> view = rules.view(reportId, parse.parseVersion());
        if (view.isEmpty()) {
            // 该版本未执行规则：不得视为安全，默认阻断归档。
            List<IndicatorDto> all = assembler.forParse(parse.id());
            return new ReportDetailRes(
                    reportId, parse.reportType(), job.status(), parse.parseVersion(), parse.active(),
                    parse.parseConfidence(), all, null, null, List.of(), List.of(), true, false, null, null,
                    structure.toSubReportDtos(), structure.sections(), structure.findings(), structure.assessments());
        }
        ReportRuleService.RuleView v = view.get();
        // 身份冲突：报告可能属于他人，不向调用方返回指标明细。
        boolean conflict = IdentityStatus.CONFLICT.name().equals(v.identityCheck().status());
        List<IndicatorDto> indicators = conflict
                ? List.of()
                : assembler.forParse(parse.id());
        StructureJson.StructurePayload exposed = conflict ? StructureJson.StructurePayload.empty() : structure;
        return new ReportDetailRes(
                reportId, parse.reportType(), job.status(), parse.parseVersion(), parse.active(),
                parse.parseConfidence(), indicators, v.identityCheck(), v.criticalCoverage(), v.abnormals(),
                v.criticalHits(), v.archiveBlocked(), v.safeNormalAllowed(), v.blockCode(), v.ruleVersion(),
                exposed.toSubReportDtos(), exposed.sections(), exposed.findings(), exposed.assessments());
    }

    /** 报告的危急事件列表；仅报告所属账号可读。 */
    @Transactional(readOnly = true)
    public CriticalEventsRes criticalEvents(String accountId, String reportId) {
        jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        return new CriticalEventsRes(reportId, rules.criticalEvents(reportId));
    }

    // ---- 内部 ----

    /** 解析版本号与对外任务状态。 */
    private record ParseOutcome(int version, String status) {
    }

    private ParseOutcome runParse(
            String reportId, ParseContext ctx, ReportParser parser, String requestKey, String expectedName,
            String documentHash) {
        ProbeReport probeReport = probeOf(parser, ctx);
        ParseResult result;
        try {
            result = parser.parse(ctx);
        } catch (BusinessException ex) {
            manifests.recordFailureNewTx(manifests.row(
                    reportId, documentHash, failureManifestVersion(),
                    parserType(parser), parserVersion(parser),
                    extractionEngine(parser), extractionEngineVersion(parser),
                    ExtractedDocument.SCHEMA_VERSION,
                    probeReport == null ? 0 : probeReport.totalPages(), 0,
                    probeReport == null ? 0 : probeReport.totalPages(),
                    DocumentQualityStatus.FAILED, List.of(),
                    probeReport == null ? null : probeReport.decision(),
                    probeReport == null ? List.of() : probeReport.reasons(),
                    "EXTRACT_OR_STRUCTURE", String.valueOf(ex.getCode())));
            throw ex;
        }
        QualityVerdict quality = ParseQualityGate.evaluate(result);
        int failedPages = result.meta().parseFailedPages().isEmpty()
                ? result.meta().failedPages().size()
                : result.meta().parseFailedPages().size();
        int totalPages = result.meta().totalPages() == null
                ? Math.max(failedPages, 1)
                : result.meta().totalPages();
        int successPages = Math.max(0, totalPages - failedPages - result.meta().lowConfidencePages().size());

        if (quality.status() == DocumentQualityStatus.FAILED) {
            manifests.recordFailureNewTx(manifests.row(
                    reportId, documentHash, failureManifestVersion(),
                    parserType(parser), parserVersion(parser),
                    extractionEngine(parser), extractionEngineVersion(parser),
                    ExtractedDocument.SCHEMA_VERSION,
                    totalPages, successPages, failedPages,
                    quality.status(), quality.issues(),
                    probeReport == null ? null : probeReport.decision(),
                    probeReport == null ? List.of() : probeReport.reasons(),
                    "QUALITY_GATE", quality.status().name()));
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED,
                    quality.issues().isEmpty() ? "质量门禁未通过" : quality.issues().get(0).message());
        }

        int version = parses.maxVersion(reportId) + 1;
        boolean activate = quality.passed();
        if (activate) {
            parses.deactivatePrevious(reportId);
        }
        String parseId = "prs_" + UUID.randomUUID().toString().replace("-", "");
        String parseStatus = activate ? STATUS_PARSED : quality.status().name();
        parses.insertParseResult(new ReportParseResultRow(
                parseId, reportId, version, activate, result.reportType().name(),
                result.parseConfidence(), parseStatus, requestKey, StructureJson.write(result.structure()),
                resolveSpecimenTime(result)));

        List<ReportIndicatorRow> rows = new ArrayList<>();
        List<ReportSourceEvidenceRow> evidences = new ArrayList<>();
        List<IdentifiedIndicator> identified = new ArrayList<>();
        for (ParsedIndicator ind : result.indicators()) {
            String indicatorId = "ind_" + UUID.randomUUID().toString().replace("-", "");
            rows.add(toRow(indicatorId, parseId, reportId, version, ind));
            if (ind.evidence() != null) {
                evidences.add(toEvidenceRow(indicatorId, ind.evidence()));
            }
            identified.add(new IdentifiedIndicator(indicatorId, ind));
        }
        parses.insertIndicators(rows);
        evidences.forEach(parses::insertEvidence);

        manifests.record(manifests.row(
                reportId, documentHash, version,
                parserType(parser), parserVersion(parser),
                extractionEngine(parser), extractionEngineVersion(parser),
                ExtractedDocument.SCHEMA_VERSION,
                totalPages, successPages, failedPages,
                quality.status(), quality.issues(),
                probeReport == null ? null : probeReport.decision(),
                probeReport == null ? List.of() : probeReport.reasons(),
                activate ? "" : "QUALITY_GATE",
                activate ? "" : quality.status().name()));

        if (!activate) {
            jobs.updateStatus(reportId, STATUS_NEED_MANUAL_REVIEW);
            return new ParseOutcome(version, STATUS_NEED_MANUAL_REVIEW);
        }

        jobs.updateStatus(reportId, STATUS_PARSED);
        SafetyVerdict verdict = rules.run(reportId, version, result, identified, expectedName);
        String status = verdict.status() == null ? STATUS_PARSED : verdict.status().name();
        return new ParseOutcome(version, status);
    }

    private static ProbeReport probeOf(ReportParser parser, ParseContext ctx) {
        if (parser instanceof NativePdfParser nativePdf) {
            try {
                return nativePdf.lastProbe(ctx);
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (parser instanceof OcrLayoutParser ocr) {
            try {
                return ocr.probeIfPdf(ctx);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static String parserType(ReportParser parser) {
        if (parser instanceof NativePdfParser) {
            return NativePdfParser.PARSER_TYPE;
        }
        if (parser instanceof OcrLayoutParser) {
            return OcrLayoutParser.PARSER_TYPE;
        }
        return parser.getClass().getSimpleName();
    }

    private static String parserVersion(ReportParser parser) {
        if (parser instanceof NativePdfParser) {
            return NativePdfParser.PARSER_VERSION;
        }
        if (parser instanceof OcrLayoutParser) {
            return OcrLayoutParser.PARSER_VERSION;
        }
        return "1.0.0";
    }

    private static String extractionEngine(ReportParser parser) {
        if (parser instanceof OcrLayoutParser) {
            return FakeDocumentExtractionAdapter.ENGINE;
        }
        return "";
    }

    private static String extractionEngineVersion(ReportParser parser) {
        if (parser instanceof OcrLayoutParser) {
            return FakeDocumentExtractionAdapter.ENGINE_VERSION;
        }
        return "";
    }

    /** 失败 Manifest 使用负版本号，避免与正式 parse_version 唯一约束冲突。 */
    private static int failureManifestVersion() {
        return -1 - (int) (Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L);
    }

    /** 趋势排序时间：优先 meta.specimenTime，否则取指标 reportTime 最早值。 */
    static Instant resolveSpecimenTime(ParseResult result) {
        if (result.meta() != null && result.meta().specimenTime() != null) {
            return result.meta().specimenTime();
        }
        return result.indicators().stream()
                .map(ParsedIndicator::reportTime)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private static ReportDetailRes emptyDetail(String reportId, String jobType, String status) {
        return new ReportDetailRes(
                reportId, jobType, status, 0, false, 0.0, List.of(),
                null, null, List.of(), List.of(), true, false, null, null,
                List.of(), List.of(), List.of(), List.of());
    }

    private CreateReportRes replay(ReportJobRow existing, String hash) {
        if (!hash.equals(existing.requestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "同一 Idempotency-Key 的请求内容不一致");
        }
        int version = parses.findActive(existing.reportId()).map(ReportParseResultRow::parseVersion).orElse(0);
        return new CreateReportRes(existing.reportId(), existing.status(), version);
    }

    private ReportType resolveType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return ReportType.LAB_PANEL;
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        try {
            return ReportType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "不支持的报告类型");
        }
    }

    private UploadGuard.Kind checkFile(UploadedFile file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "file 不能为空");
        }
        ReportProperties.Parse p = properties.getParse();
        return UploadGuard.check(
                file.fileName(), file.contentType(), file.content(), p.getMaxFileBytes(), p.isAllowTextFixture());
    }

    private static String normalizeExpectedName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "expectedPatientName 过长");
        }
        return name;
    }

    private static String requestHash(String patientId, ReportType type, String expectedName, byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(patientId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(type.name().getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update((expectedName == null ? "" : expectedName).getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(content);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ReportIndicatorRow toRow(
            String id, String parseId, String reportId, int version, ParsedIndicator i) {
        FieldConfidence c = i.confidence();
        return new ReportIndicatorRow(
                id, parseId, reportId, version,
                i.rawName(), i.rawValue(), i.rawUnit(), i.canonicalCode(), i.specimenType(),
                i.normalizedValue(), i.normalizedUnit(), i.normalizationStatus(), i.refLow(), i.refHigh(),
                c.ocr(), c.structure(), c.extraction(), c.normalization(),
                c.name(), c.value(), c.unit(), c.refRange(), i.subReportId(), i.reportTime());
    }

    private ReportSourceEvidenceRow toEvidenceRow(String indicatorId, SourceEvidence ev) {
        String regionJson = null;
        if (ev.region() != null) {
            try {
                regionJson = json.writeValueAsString(ev.region());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return new ReportSourceEvidenceRow(
                "evd_" + UUID.randomUUID().toString().replace("-", ""),
                indicatorId, ev.pageIndex(), ev.rawText(), regionJson);
    }

}