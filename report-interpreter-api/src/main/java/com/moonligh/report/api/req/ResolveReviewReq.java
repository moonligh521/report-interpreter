package com.moonligh.report.api.req;

/** 危急 / 覆盖类人工复核结案（不产生普通字段修订）。 */
public record ResolveReviewReq(String resolution, String note) {
}
