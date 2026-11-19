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
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseQualityGate;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ParserRegistry;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.domain.parse.UploadGuard;
import com.moonligh.report.domain.rule.IdentifiedIndicator;
import com.moonligh.report.domain.rule.IdentityStatus;
import com.moonligh.report.domain.rule.SafetyVerdict;
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
    private static final String UNKNOWN_TYPE = "UNKNOWN";
    private static final int MAX_NAME_LENGTH = 128;

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final ParserRegistry registry;
    private final ReportProperties properties;
    private final ReportRuleService rules;
    private final IndicatorDtoAssembler assembler;
    private final ObjectMapper json = new ObjectMapper();

    public ReportParseService(
            ReportJobStore jobs, ReportParseStore parses, ParserRegistry registry, ReportProperties properties,
            ReportRuleService rules, IndicatorDtoAssembler assembler) {
        this.assembler = assembler;
        this.jobs = jobs;
        this.parses = parses;
        this.registry = registry;
        this.properties = properties;
        this.rules = rules;
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
            // 二进制 PDF/图片：P0 尚未接入 OCR 引擎，仅建单。
            return new CreateReportRes(reportId, STATUS_CREATED, 0);
        }
        ParseOutcome outcome = runParse(reportId, ctx, parser.get(), null, expectedName);
        return new CreateReportRes(reportId, outcome.verdict().status().name(), outcome.version());
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
        ParseOutcome outcome = runParse(reportId, ctx, parser, idempotencyKey, job.expectedPatientName());
        return new CreateReportRes(reportId, outcome.verdict().status().name(), outcome.version());
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

    /** 解析版本号与规则出口结论。 */
    private record ParseOutcome(int version, SafetyVerdict verdict) {
    }

    private ParseOutcome runParse(
            String reportId, ParseContext ctx, ReportParser parser, String requestKey, String expectedName) {
        ParseResult result = parser.parse(ctx);
        ParseQualityGate.check(result);
        int version = parses.maxVersion(reportId) + 1;
        parses.deactivatePrevious(reportId);
        String parseId = "prs_" + UUID.randomUUID().toString().replace("-", "");
        parses.insertParseResult(new ReportParseResultRow(
                parseId, reportId, version, true, result.reportType().name(),
                result.parseConfidence(), STATUS_PARSED, requestKey, StructureJson.write(result.structure()),
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
        jobs.updateStatus(reportId, STATUS_PARSED);
        // PARSED 之后自动执行规则：身份 / 覆盖 / 参考区间 / 危急，状态推进到规则出口。
        SafetyVerdict verdict = rules.run(reportId, version, result, identified, expectedName);
        return new ParseOutcome(version, verdict);
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