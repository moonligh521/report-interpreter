package com.moonligh.report.infrastructure.archive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.moonligh.report.api.res.FhirHints;
import com.moonligh.report.api.res.IndicatorDto;
import com.moonligh.report.api.res.LoincMapping;

/**
 * 可选 FHIR 提示：按已知规范码给出 LOINC 对照；非权威、可空。
 * 内部字典用强类型条目，不把 Map 暴露为 API/领域契约。
 */
public final class FhirHintFactory {

    private record Entry(String loinc, String display) {
    }

    private static final Map<String, Entry> LOINC = Map.of(
            "ALT", new Entry("1742-6", "Alanine aminotransferase [Enzymatic activity/volume] in Serum or Plasma"),
            "AST", new Entry("1920-8", "Aspartate aminotransferase [Enzymatic activity/volume] in Serum or Plasma"),
            "K", new Entry("2823-3", "Potassium [Moles/volume] in Serum or Plasma"),
            "NA", new Entry("2951-2", "Sodium [Moles/volume] in Serum or Plasma"),
            "CREA", new Entry("2160-0", "Creatinine [Mass/volume] in Serum or Plasma"),
            "GLU", new Entry("2345-7", "Glucose [Mass/volume] in Serum or Plasma"));

    private FhirHintFactory() {
    }

    public static FhirHints forIndicators(List<IndicatorDto> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            return null;
        }
        Map<String, LoincMapping> unique = new LinkedHashMap<>();
        for (IndicatorDto ind : indicators) {
            if (ind.canonicalCode() == null || ind.canonicalCode().isBlank()) {
                continue;
            }
            String code = ind.canonicalCode().trim().toUpperCase(Locale.ROOT);
            Entry e = LOINC.get(code);
            if (e != null) {
                unique.putIfAbsent(code, new LoincMapping(code, e.loinc(), e.display()));
            }
        }
        if (unique.isEmpty()) {
            return null;
        }
        return new FhirHints("Observation", new ArrayList<>(unique.values()));
    }
}
