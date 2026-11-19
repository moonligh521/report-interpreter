package com.moonligh.report.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
import com.moonligh.report.api.req.CorrectionPatch;
import com.moonligh.report.api.req.CorrectionReq;
import com.moonligh.report.api.res.CorrectionRes;
import com.moonligh.report.api.res.FieldConfidence;
import com.moonligh.report.api.res.SourceEvidence;
import com.moonligh.report.domain.job.ReportJobStore;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportParseStore;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.domain.rule.IdentifiedIndicator;
import com.moonligh.report.infrastructure.persistence.ReportCorrectionMapper;
import com.moonligh.report.infrastructure.persistence.ReportCorrectionRow;
import com.moonligh.report.infrastructure.persistence.ReportIndicatorRow;
import com.moonligh.report.infrastructure.persistence.ReportJobRow;
import com.moonligh.report.infrastructure.persistence.ReportParseResultRow;
import com.moonligh.report.infrastructure.persistence.ReportSourceEvidenceRow;

/**
 * 普通字段修订：新 parseVersion + 重跑规则（M-R5）。与危急复核接口分离（M-R6）。
 */
@Service
public class ReportCorrectionService {

    private final ReportJobStore jobs;
    private final ReportParseStore parses;
    private final ReportRuleService rules;
    private final ReportCorrectionMapper corrections;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public ReportCorrectionService(
            ReportJobStore jobs, ReportParseStore parses, ReportRuleService rules, ReportCorrectionMapper corrections) {
        this.jobs = jobs;
        this.parses = parses;
        this.rules = rules;
        this.corrections = corrections;
    }

    @Transactional
    public CorrectionRes correct(CorrectionReq req, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少或非法 Idempotency-Key");
        }
        if (req == null || !StringUtils.hasText(req.reportId()) || !StringUtils.hasText(req.reviewerId())) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "reportId/reviewerId 不能为空");
        }
        if (req.patches() == null || req.patches().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "patches 不能为空");
        }
        Optional<ReportCorrectionRow> existing = Optional.ofNullable(corrections.findByIdempotency(idempotencyKey));
        if (existing.isPresent()) {
            return replay(existing.get(), req);
        }

        ReportJobRow job = jobs.lockById(req.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "找不到该报告任务"));
        ReportParseResultRow base = parses.findActive(req.reportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "报告尚无解析结果"));
        if (base.parseVersion() != req.parseVersionBase()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "parseVersionBase 与当前 active 版本不一致");
        }

        for (CorrectionPatch p : req.patches()) {
            if (!StringUtils.hasText(p.indicatorId()) || p.normalizedValue() == null || !StringUtils.hasText(p.reason())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "patch 字段不完整");
            }
        }

        List<ReportIndicatorRow> oldIndicators = parses.listIndicators(base.id());
        for (CorrectionPatch p : req.patches()) {
            if (oldIndicators.stream().noneMatch(r -> r.id().equals(p.indicatorId()))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "指标不存在: " + p.indicatorId());
            }
        }

        int newVersion = parses.maxVersion(req.reportId()) + 1;
        parses.deactivatePrevious(req.reportId());
        String parseId = "prs_" + UUID.randomUUID().toString().replace("-", "");
        parses.insertParseResult(new ReportParseResultRow(
                parseId, req.reportId(), newVersion, true, base.reportType(), base.parseConfidence(),
                ReportParseService.STATUS_PARSED, idempotencyKey, base.structureJson()));

        List<ReportIndicatorRow> newRows = new ArrayList<>();
        List<IdentifiedIndicator> identified = new ArrayList<>();
        List<ParsedIndicator> parsed = new ArrayList<>();
        for (ReportIndicatorRow old : oldIndicators) {
            BigDecimal value = patchedValue(req.patches(), old);
            String newId = "ind_" + UUID.randomUUID().toString().replace("-", "");
            ReportIndicatorRow row = new ReportIndicatorRow(
                    newId, parseId, req.reportId(), newVersion,
                    old.rawName(), old.rawValue(), old.rawUnit(), old.canonicalCode(), old.specimenType(),
                    value, old.normalizedUnit(), old.normalizationStatus(), old.refLow(), old.refHigh(),
                    old.confOcr(), old.confStructure(), old.confExtraction(), old.confNormalization(),
                    old.confName(), old.confValue(), old.confUnit(), old.confRefRange(),
                    old.subReportId(), old.reportTime());
            newRows.add(row);
            SourceEvidence evidence = parses.findEvidence(old.id())
                    .map(e -> new SourceEvidence(e.pageIndex(), e.rawText(), null))
                    .orElse(new SourceEvidence(0, old.rawName() + " " + old.rawValue(), null));
            ParsedIndicator ind = new ParsedIndicator(
                    old.rawName(), old.rawValue(), old.rawUnit(), old.canonicalCode(), old.specimenType(),
                    value, old.normalizedUnit(), old.normalizationStatus(), old.refLow(), old.refHigh(),
                    new FieldConfidence(
                            old.confOcr(), old.confStructure(), old.confExtraction(), old.confNormalization(),
                            old.confName(), old.confValue(), old.confUnit(), old.confRefRange()),
                    evidence, old.subReportId(), old.reportTime());
            parsed.add(ind);
            identified.add(new IdentifiedIndicator(newId, ind));
            parses.insertEvidence(new ReportSourceEvidenceRow(
                    "evd_" + UUID.randomUUID().toString().replace("-", ""),
                    newId, evidence.pageIndex(), evidence.rawText(), null));
        }
        parses.insertIndicators(newRows);

        ReportType type = ReportType.valueOf(base.reportType());
        ParseResult result = new ParseResult(
                type, parsed, base.parseConfidence(), null, StructureJson.toDomain(StructureJson.read(base.structureJson())));
        rules.run(req.reportId(), newVersion, result, identified, job.expectedPatientName());

        ReportCorrectionRow row = new ReportCorrectionRow(
                "cor_" + UUID.randomUUID().toString().replace("-", ""), req.reportId(), req.parseVersionBase(),
                newVersion, req.reviewerId().strip(), writePatches(req.patches()), idempotencyKey, null);
        try {
            corrections.insert(row);
        } catch (DuplicateKeyException e) {
            return replay(corrections.findByIdempotency(idempotencyKey), req);
        }
        return new CorrectionRes(newVersion, true);
    }

    private static CorrectionRes replay(ReportCorrectionRow row, CorrectionReq req) {
        boolean same = row.reportId().equals(req.reportId())
                && row.parseVersionBase() == req.parseVersionBase()
                && row.reviewerId().equals(req.reviewerId().strip());
        if (!same) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "同一 Idempotency-Key 的请求内容不一致");
        }
        return new CorrectionRes(row.newParseVersion(), true);
    }

    private static BigDecimal patchedValue(List<CorrectionPatch> patches, ReportIndicatorRow old) {
        for (CorrectionPatch p : patches) {
            if (old.id().equals(p.indicatorId())) {
                return p.normalizedValue();
            }
        }
        return old.normalizedValue();
    }

    private String writePatches(List<CorrectionPatch> patches) {
        try {
            return json.writeValueAsString(patches);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
