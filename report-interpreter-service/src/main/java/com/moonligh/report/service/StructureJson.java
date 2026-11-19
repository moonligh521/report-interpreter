package com.moonligh.report.service;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.api.res.AssessmentDto;
import com.moonligh.report.api.res.FindingDto;
import com.moonligh.report.api.res.SectionDto;
import com.moonligh.report.api.res.SubReportDto;
import com.moonligh.report.domain.parse.ParsedAssessment;
import com.moonligh.report.domain.parse.ParsedFinding;
import com.moonligh.report.domain.parse.ParsedSection;
import com.moonligh.report.domain.parse.ParsedStructure;
import com.moonligh.report.domain.parse.ParsedSubReport;

/** structure_json ↔ DTO / 领域结构互转（强类型，禁 Map；时间用 ISO 字符串）。 */
final class StructureJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private StructureJson() {
    }

    /** JSON 载体：reportTime 以 ISO-8601 字符串存储，避免额外 Jackson JSR310 依赖。 */
    record StructurePayload(
            List<SubReportWire> subReports,
            List<SectionDto> sections,
            List<FindingDto> findings,
            List<AssessmentDto> assessments) {
        static StructurePayload empty() {
            return new StructurePayload(List.of(), List.of(), List.of(), List.of());
        }

        List<SubReportDto> toSubReportDtos() {
            return subReports == null ? List.of() : subReports.stream().map(SubReportWire::toDto).toList();
        }
    }

    record SubReportWire(String subReportId, String title, String reportTime, Integer pageIndex) {
        SubReportDto toDto() {
            Instant t = reportTime == null || reportTime.isBlank() ? null : Instant.parse(reportTime);
            return new SubReportDto(subReportId, title, t, pageIndex);
        }

        static SubReportWire from(ParsedSubReport s) {
            return new SubReportWire(
                    s.subReportId(), s.title(),
                    s.reportTime() == null ? null : s.reportTime().toString(), s.pageIndex());
        }
    }

    static String write(ParsedStructure structure) {
        StructurePayload p = fromDomain(structure);
        try {
            return JSON.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static StructurePayload read(String json) {
        if (json == null || json.isBlank()) {
            return StructurePayload.empty();
        }
        try {
            StructurePayload p = JSON.readValue(json, StructurePayload.class);
            return p == null ? StructurePayload.empty() : p;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static StructurePayload fromDomain(ParsedStructure structure) {
        ParsedStructure s = structure == null ? ParsedStructure.EMPTY : structure;
        return new StructurePayload(
                s.subReports().stream().map(SubReportWire::from).toList(),
                s.sections().stream().map(x -> new SectionDto(x.id(), x.title())).toList(),
                s.findings().stream()
                        .map(x -> new FindingDto(x.id(), x.sectionId(), x.text(), x.pageIndex()))
                        .toList(),
                s.assessments().stream().map(x -> new AssessmentDto(x.id(), x.text())).toList());
    }

    static ParsedStructure toDomain(StructurePayload p) {
        StructurePayload s = p == null ? StructurePayload.empty() : p;
        List<SubReportWire> subs = s.subReports() == null ? List.of() : s.subReports();
        return new ParsedStructure(
                subs.stream()
                        .map(x -> new ParsedSubReport(
                                x.subReportId(), x.title(),
                                x.reportTime() == null || x.reportTime().isBlank()
                                        ? null : Instant.parse(x.reportTime()),
                                x.pageIndex()))
                        .toList(),
                s.sections() == null ? List.of()
                        : s.sections().stream().map(x -> new ParsedSection(x.id(), x.title())).toList(),
                s.findings() == null ? List.of()
                        : s.findings().stream()
                                .map(x -> new ParsedFinding(x.id(), x.sectionId(), x.text(), x.pageIndex()))
                                .toList(),
                s.assessments() == null ? List.of()
                        : s.assessments().stream().map(x -> new ParsedAssessment(x.id(), x.text())).toList());
    }
}
