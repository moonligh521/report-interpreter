package com.moonligh.report.api.req;

import java.util.List;

/** 普通字段修订（与危急复核分离）。 */
public record CorrectionReq(
        String reportId, int parseVersionBase, List<CorrectionPatch> patches, String reviewerId) {
}
