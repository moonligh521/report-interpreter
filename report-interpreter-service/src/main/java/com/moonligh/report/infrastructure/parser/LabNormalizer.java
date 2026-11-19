package com.moonligh.report.infrastructure.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 检验指标规范化：只产出 normalized 结果，永不修改 raw。
 */
final class LabNormalizer {

    static final String NORMALIZED = "NORMALIZED";
    static final String CONVERTED = "CONVERTED";
    static final String UNIT_UNMAPPED = "UNIT_UNMAPPED";
    static final String NAME_UNMAPPED = "NAME_UNMAPPED";
    static final String VALUE_NOT_NUMERIC = "VALUE_NOT_NUMERIC";

    /** 规范化结果。 */
    record Outcome(
            String canonicalCode, String specimenType, BigDecimal value, String unit, String status) {
    }

    /** 指标字典项：规范码、标本、标准单位、可换算单位到标准单位的乘数。 */
    private record Spec(String code, String specimen, String unit, Map<String, BigDecimal> factors) {
    }

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            entry("ALT", new Spec("ALT", "SERUM", "U/L", Map.of())),
            entry("AST", new Spec("AST", "SERUM", "U/L", Map.of())),
            entry("K", new Spec("K", "SERUM", "mmol/L", Map.of("meq/l", BigDecimal.ONE))),
            entry("NA", new Spec("NA", "SERUM", "mmol/L", Map.of("meq/l", BigDecimal.ONE))),
            entry("CREA", new Spec("CREA", "SERUM", "umol/L", Map.of("mg/dl", new BigDecimal("88.4")))),
            entry("GLU", new Spec("GLU", "SERUM", "mmol/L", Map.of("mg/dl", new BigDecimal("0.0555")))));

    private static final Map<String, String> ALIASES = Map.ofEntries(
            entry("ALT", "ALT"), entry("GPT", "ALT"), entry("丙氨酸氨基转移酶", "ALT"),
            entry("AST", "AST"), entry("GOT", "AST"),
            entry("K", "K"), entry("钾", "K"), entry("NA", "NA"), entry("钠", "NA"),
            entry("CREA", "CREA"), entry("CR", "CREA"), entry("肌酐", "CREA"),
            entry("GLU", "GLU"), entry("葡萄糖", "GLU"));

    private LabNormalizer() {
    }

    private static <V> Map.Entry<String, V> entry(String k, V v) {
        return Map.entry(k, v);
    }

    static Outcome normalize(String rawName, String rawValue, String rawUnit) {
        Optional<Spec> spec = lookup(rawName);
        if (spec.isEmpty()) {
            return new Outcome(null, null, null, null, NAME_UNMAPPED);
        }
        Spec s = spec.get();
        BigDecimal value = parseNumber(rawValue);
        if (value == null) {
            return new Outcome(s.code(), s.specimen(), null, null, VALUE_NOT_NUMERIC);
        }
        String unit = rawUnit == null ? "" : rawUnit.trim();
        if (unit.equalsIgnoreCase(s.unit())) {
            return new Outcome(s.code(), s.specimen(), value, s.unit(), NORMALIZED);
        }
        BigDecimal factor = s.factors().get(unit.toLowerCase(Locale.ROOT));
        if (factor == null) {
            return new Outcome(s.code(), s.specimen(), null, null, UNIT_UNMAPPED);
        }
        BigDecimal converted = value.multiply(factor).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return new Outcome(s.code(), s.specimen(), converted, s.unit(), CONVERTED);
    }

    static BigDecimal parseNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<Spec> lookup(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }
        String code = ALIASES.get(rawName.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(code).map(SPECS::get);
    }
}