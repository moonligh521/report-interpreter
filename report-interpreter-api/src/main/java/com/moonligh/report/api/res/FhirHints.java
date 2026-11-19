package com.moonligh.report.api.res;

import java.util.List;

/**
 * 可选 FHIR 适配提示字段。本仓不实现 FHIR Server；仅供 clinical-record 等下游参考。
 */
public record FhirHints(String resourceType, List<LoincMapping> loincMappings) {

    public FhirHints {
        loincMappings = loincMappings == null ? List.of() : List.copyOf(loincMappings);
    }
}
