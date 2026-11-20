package com.moonligh.report.domain.parse;

/**
 * 报告类型。P0：LAB_PANEL；P1：CHECKUP_BOOK / ECG_REPORT / US_REPORT；
 * P2：DISCHARGE_SUMMARY / ENDOSCOPY_REPORT。
 * IMAGING_FILM 与其它未知类型一律拒绝（4101）。
 */
public enum ReportType {
    LAB_PANEL,
    CHECKUP_BOOK,
    ECG_REPORT,
    US_REPORT,
    DISCHARGE_SUMMARY,
    ENDOSCOPY_REPORT
}
