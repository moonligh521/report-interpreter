package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.api.res.BoundingBox;
import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.domain.parse.ExtractedBlock;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractedPage;
import com.moonligh.report.domain.parse.ExtractedTable;
import com.moonligh.report.domain.parse.ExtractedTableCell;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.PageParseStatus;

/** Task1 / RP-5：ExtractedDocument 契约序列化往返、禁 Map、schemaVersion 必填。 */
class ExtractedDocumentContractTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void schemaVersionRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractedDocument(
                1, List.of(), 0.9, 0.9, null, "fake-1"));
        assertThrows(IllegalArgumentException.class, () -> new ExtractedDocument(
                1, List.of(), 0.9, 0.9, "  ", "fake-1"));
    }

    @Test
    void serializationRoundTripPreservesTypedFields() throws Exception {
        ExtractedDocument original = sample();
        String payload = json.writeValueAsString(original);
        ExtractedDocument restored = json.readValue(payload, ExtractedDocument.class);
        assertEquals(original, restored);
        assertEquals(ExtractedDocument.SCHEMA_VERSION, restored.schemaVersion());
        assertEquals(PageParseStatus.SUCCESS, restored.pages().get(0).status());
        assertEquals("钾", restored.pages().get(0).blocks().get(0).text());
    }

    @Test
    void jsonHasNoFreeFormMapObject() throws Exception {
        JsonNode tree = json.valueToTree(sample());
        assertTrue(tree.isObject());
        assertTrue(tree.get("pages").isArray());
        assertTrue(tree.get("pages").get(0).get("blocks").isArray());
        assertTrue(tree.get("pages").get(0).get("tables").isArray());
        // 契约字段固定，不得出现任意扩展 Map 节点。
        assertEquals(
                List.of("totalPages", "pages", "ocrConfidence", "layoutConfidence", "schemaVersion", "engineVersion"),
                fieldNames(tree));
    }

    @Test
    void extractionRequestRequiresIdsAndSchema() {
        assertThrows(IllegalArgumentException.class, () -> new ExtractionRequest(
                null, "hash", ExtractedDocument.SCHEMA_VERSION, "application/pdf", new byte[] {1}, "a.pdf", "k"));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionRequest(
                "r1", " ", ExtractedDocument.SCHEMA_VERSION, "application/pdf", new byte[] {1}, "a.pdf", "k"));
        DocumentExtractionPort port = req -> sample();
        ExtractedDocument doc = port.extract(new ExtractionRequest(
                "r1", "abc", ExtractedDocument.SCHEMA_VERSION, "image/png", new byte[] {1}, "a.png", "k"));
        assertEquals(1, doc.totalPages());
    }

    private static ExtractedDocument sample() {
        BoundingBox box = new BoundingBox(0.1, 0.2, 0.3, 0.05);
        ExtractedBlock block = new ExtractedBlock("TEXT", "钾", box, 0.95);
        ExtractedTableCell cell = new ExtractedTableCell(0, 0, "4.2", box);
        ExtractedTable table = new ExtractedTable("t1", box, List.of(cell));
        ExtractedPage page = new ExtractedPage(
                0, "钾 4.2 mmol/L", List.of(block), List.of(table), PageParseStatus.SUCCESS, 0.96);
        return new ExtractedDocument(1, List.of(page), 0.94, 0.91, ExtractedDocument.SCHEMA_VERSION, "fake-1.0");
    }

    private static List<String> fieldNames(JsonNode node) {
        return node.properties().stream().map(e -> e.getKey()).toList();
    }
}
