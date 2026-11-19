package com.moonligh.report.api.res;

/**
 * 危急覆盖：status 取 COMPLETE / PARTIAL / UNKNOWN，必须附带 evidence。
 */
public record CriticalCoverageRes(String status, CriticalCoverageEvidence evidence) {
}