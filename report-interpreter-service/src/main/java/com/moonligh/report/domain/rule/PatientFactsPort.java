package com.moonligh.report.domain.rule;

import java.time.Instant;

/**
 * 人口学端口：身份服务或 Fake。失败不得编造，应返回 UNKNOWN + incomplete。
 */
public interface PatientFactsPort {

    PatientFactsResolution resolve(String patientId, Instant asOf);
}
