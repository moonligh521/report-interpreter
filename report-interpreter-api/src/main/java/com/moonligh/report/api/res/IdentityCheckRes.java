package com.moonligh.report.api.res;

/**
 * 身份一致性检查结果。status 取 MATCHED / MISSING / CONFLICT；detail 不含姓名等个人信息。
 */
public record IdentityCheckRes(String status, String detail) {
}