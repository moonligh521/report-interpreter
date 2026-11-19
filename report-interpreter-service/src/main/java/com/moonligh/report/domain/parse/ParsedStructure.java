package com.moonligh.report.domain.parse;

import java.util.List;

/** 多类型结构骨架：子报告 / 段落 / 所见 / 结论。LAB_PANEL 为空。 */
public record ParsedStructure(
        List<ParsedSubReport> subReports,
        List<ParsedSection> sections,
        List<ParsedFinding> findings,
        List<ParsedAssessment> assessments) {

    public static final ParsedStructure EMPTY =
            new ParsedStructure(List.of(), List.of(), List.of(), List.of());

    public ParsedStructure {
        subReports = subReports == null ? List.of() : List.copyOf(subReports);
        sections = sections == null ? List.of() : List.copyOf(sections);
        findings = findings == null ? List.of() : List.copyOf(findings);
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
    }

    public boolean isEmpty() {
        return subReports.isEmpty() && sections.isEmpty() && findings.isEmpty() && assessments.isEmpty();
    }
}
