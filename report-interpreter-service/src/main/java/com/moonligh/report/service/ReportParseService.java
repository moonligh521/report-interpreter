package com.moonligh.report.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
import com.moonligh.report.api.res.BoundingBox;
import com.moonligh.report.api.res.CreateReportRes;
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

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final ParserRegistry registry;
    private final ReportProperties properties;
    private final ObjectMapper json = new ObjectMapper();

    public ReportParseService(
            ReportJobStore jobs, ReportParseStore parses, ParserRegistry registry, ReportProperties properties) {
        this.jobs = jobs;
        this.parses = parses;
        this.registry = registry;
        this.properties = properties;
    }

    @Transactional
    public CreateReportRes create(
            String accountId, String patientId, String idempotencyKey, String reportType, UploadedFile file) {
        if (!StringUtils.hasText(patientId)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "patientId 不能为空");
        }
        ReportType type = resolveType(reportType);
        UploadGuard.Kind kind = checkFile(file);
        String hash = requestHash(patientId, type, file.content());

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
                type.name(), hash, sourceText, null, null);
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
        int version = runParse(reportId, ctx, parser.get(), null);
        return new CreateReportRes(reportId, STATUS_PARSED, version);
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
        ParseContext ctx = new ParseContext(
                reportId, type, "reparse.lab.txt", "text/plain", job.sourceText().getBytes(StandardCharsets.UTF_8));
        ReportParser parser = registry.find(ctx)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "无可用解析器"));
        int version = runParse(reportId, ctx, parser, idempotencyKey);
        return new CreateReportRes(reportId, STATUS_PARSED, version);
    }

    @Transactional(readOnly = true)
    public ReportDetailRes get(String accountId, String reportId) {
        ReportJobRow job = jobs.findById(reportId)
                .filter(j -> j.accountId().equals(accountId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        String jobType = job.reportType() == null ? UNKNOWN_TYPE : job.reportType();
        Optional<ReportParseResultRow> active = parses.findActive(reportId);
        if (active.isEmpty()) {
            return new ReportDetailRes(reportId, jobType, job.status(), 0, false, 0.0, List.of());
        }
        ReportParseResultRow parse = active.get();
        List<IndicatorDto> indicators = parses.listIndicators(parse.id()).stream().map(this::toDto).toList();
        return new ReportDetailRes(
                reportId, parse.reportType(), job.status(), parse.parseVersion(), parse.active(),
                parse.parseConfidence(), indicators);
    }

    // ---- 内部 ----

    private int runParse(String reportId, ParseContext ctx, ReportParser parser, String requestKey) {
        ParseResult result = parser.parse(ctx);
        ParseQualityGate.check(result);
        int version = parses.maxVersion(reportId) + 1;
        parses.deactivatePrevious(reportId);
        String parseId = "prs_" + UUID.randomUUID().toString().replace("-", "");
        parses.insertParseResult(new ReportParseResultRow(
                parseId, reportId, version, true, result.reportType().name(),
                result.parseConfidence(), STATUS_PARSED, requestKey));

        List<ReportIndicatorRow> rows = new ArrayList<>();
        List<ReportSourceEvidenceRow> evidences = new ArrayList<>();
        for (ParsedIndicator ind : result.indicators()) {
            String indicatorId = "ind_" + UUID.randomUUID().toString().replace("-", "");
            rows.add(toRow(indicatorId, parseId, reportId, version, ind));
            evidences.add(toEvidenceRow(indicatorId, ind.evidence()));
        }
        parses.insertIndicators(rows);
        evidences.forEach(parses::insertEvidence);
        jobs.updateStatus(reportId, STATUS_PARSED);
        return version;
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
        if (ReportType.LAB_PANEL.name().equals(raw.strip().toUpperCase(Locale.ROOT))) {
            return ReportType.LAB_PANEL;
        }
        throw new BusinessException(ErrorCode.REPORT_TYPE_UNSUPPORTED, "仅支持 LAB_PANEL 报告");
    }

    private UploadGuard.Kind checkFile(UploadedFile file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "file 不能为空");
        }
        ReportProperties.Parse p = properties.getParse();
        return UploadGuard.check(
                file.fileName(), file.contentType(), file.content(), p.getMaxFileBytes(), p.isAllowTextFixture());
    }

    private static String requestHash(String patientId, ReportType type, byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(patientId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
            md.update(type.name().getBytes(StandardCharsets.UTF_8));
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
                c.name(), c.value(), c.unit(), c.refRange());
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

    private IndicatorDto toDto(ReportIndicatorRow r) {
        FieldConfidence conf = new FieldConfidence(
                r.confOcr(), r.confStructure(), r.confExtraction(), r.confNormalization(),
                r.confName(), r.confValue(), r.confUnit(), r.confRefRange());
        SourceEvidence evidence = parses.findEvidence(r.id())
                .map(e -> new SourceEvidence(e.pageIndex(), e.rawText(), readRegion(e.regionJson())))
                .orElse(null);
        return new IndicatorDto(
                r.id(), r.rawName(), r.rawValue(), r.rawUnit(), r.canonicalCode(), r.specimenType(),
                stripScale(r.normalizedValue()), r.normalizedUnit(), r.normalizationStatus(),
                stripScale(r.refLow()), stripScale(r.refHigh()), conf, evidence);
    }

    private BoundingBox readRegion(String regionJson) {
        if (!StringUtils.hasText(regionJson)) {
            return null;
        }
        try {
            return json.readValue(regionJson, BoundingBox.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** DECIMAL(20,6) 读回带尾零，对外去掉以保持数值干净（86.000000 → 86）。 */
    private static BigDecimal stripScale(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}