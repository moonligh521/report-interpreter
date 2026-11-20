package com.moonligh.report.domain.rule;

/**
 * 规则可用的人群事实（经 {@link PatientFactsPort}；身份失败或缺字段时使用 UNKNOWN）。
 * 病情主数据不在此对象；归 clinical-record / HIS。
 */
public record PatientFacts(Integer age, String sex, String pregnancyStatus) {

    public static final PatientFacts UNKNOWN = new PatientFacts(null, null, null);
}
