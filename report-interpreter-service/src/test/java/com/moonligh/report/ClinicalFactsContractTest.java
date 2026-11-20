package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.domain.rule.ClinicalCatalog;
import com.moonligh.report.domain.rule.ClinicalCatalogPort;
import com.moonligh.report.domain.rule.CriticalRuleTable;
import com.moonligh.report.domain.rule.FactsSource;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.PatientFactsPort;
import com.moonligh.report.domain.rule.PatientFactsResolution;
import com.moonligh.report.domain.rule.ReferenceRangeRule;

/** Task1 / CF：人口学与目录契约序列化往返、禁 Map、UNKNOWN⇒incomplete。 */
class ClinicalFactsContractTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void unknownFactsAreIncomplete() {
        PatientFactsResolution r = PatientFactsResolution.unknown(FactsSource.NONE, null);
        assertEquals(PatientFacts.UNKNOWN, r.facts());
        assertTrue(r.incomplete());
        assertEquals(FactsSource.NONE, r.source());
    }

    @Test
    void resolutionRequiresFactsAndSource() {
        assertThrows(IllegalArgumentException.class, () -> new PatientFactsResolution(
                null, FactsSource.FAKE, false, null));
        assertThrows(IllegalArgumentException.class, () -> new PatientFactsResolution(
                PatientFacts.UNKNOWN, null, true, null));
    }

    @Test
    void resolutionSerializationRoundTrip() throws Exception {
        PatientFactsResolution original = new PatientFactsResolution(
                new PatientFacts(42, "M", null), FactsSource.FAKE, false, null);
        PatientFactsResolution restored = json.readValue(json.writeValueAsString(original), PatientFactsResolution.class);
        assertEquals(original, restored);
        JsonNode tree = json.valueToTree(original);
        assertEquals(List.of("facts", "source", "incomplete", "detailCode"), fieldNames(tree));
        assertEquals(List.of("age", "sex", "pregnancyStatus"), fieldNames(tree.get("facts")));
    }

    @Test
    void catalogRequiresVersionAndTypedTables() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ClinicalCatalog(
                null, CriticalRuleTable.builtIn().all(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ClinicalCatalog(
                "  ", CriticalRuleTable.builtIn().all(), List.of()));
        ClinicalCatalog catalog = sampleCatalog();
        ClinicalCatalog restored = json.readValue(json.writeValueAsString(catalog), ClinicalCatalog.class);
        assertEquals(catalog.version(), restored.version());
        assertEquals(1, restored.referenceRules().size());
        assertEquals("REF-AGE-K-ADULT", restored.referenceRules().get(0).ruleId());
        assertEquals(2, restored.criticalRuleTable().all().size());
        JsonNode tree = json.valueToTree(catalog);
        assertEquals(List.of("version", "criticalRules", "referenceRules"), fieldNames(tree));
        assertTrue(tree.get("criticalRules").isArray());
        assertTrue(tree.get("referenceRules").isArray());
    }

    @Test
    void portsAreInvokableWithTypedResults() {
        PatientFactsPort facts = (patientId, asOf) -> PatientFactsResolution.unknown(FactsSource.FAKE, "MISSING");
        ClinicalCatalogPort catalogs = version -> {
            if (!"builtin-v1".equals(version)) {
                throw new IllegalArgumentException("unknown version");
            }
            return sampleCatalog();
        };
        assertTrue(facts.resolve("p-1", Instant.parse("2026-11-20T00:00:00Z")).incomplete());
        assertEquals("builtin-v1", catalogs.load("builtin-v1").version());
        assertThrows(IllegalArgumentException.class, () -> catalogs.load("missing"));
    }

    private static ClinicalCatalog sampleCatalog() {
        ReferenceRangeRule adultK = new ReferenceRangeRule(
                "REF-AGE-K-ADULT",
                "builtin-v1",
                "K",
                "SERUM",
                "mmol/L",
                new BigDecimal("3.5"),
                new BigDecimal("5.3"),
                18,
                null,
                null);
        return new ClinicalCatalog("builtin-v1", CriticalRuleTable.builtIn().all(), List.of(adultK));
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(e -> e.getKey()).toList();
    }
}
