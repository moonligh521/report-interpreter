package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.PageParseStatus;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;

class FakeDocumentExtractionAdapterTest {

    private final FakeDocumentExtractionAdapter fake = new FakeDocumentExtractionAdapter();

    @Test
    void scanImageReturnsStableDocumentWithPageStatus() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2, 3};
        ExtractedDocument doc = fake.extract(req("image/png", "scan.png", png));
        assertEquals(1, doc.totalPages());
        assertEquals(PageParseStatus.SUCCESS, doc.pages().get(0).status());
        assertEquals(ExtractedDocument.SCHEMA_VERSION, doc.schemaVersion());
        assertTrue(doc.pages().get(0).rawText().contains("REPORT_TYPE=LAB_PANEL"));
    }

    @Test
    void fixtureCanMarkFailedPage() {
        String body = "---PAGE---\nSTATUS=FAILED\nbad\n---PAGE---\nREPORT_TYPE=LAB_PANEL\nK|4.2|mmol/L|3.5|5.5|K 4.2|0|SERUM\n";
        byte[] content = (FakeDocumentExtractionAdapter.FIXTURE_PREFIX + body).getBytes(StandardCharsets.UTF_8);
        ExtractedDocument doc = fake.extract(req("application/octet-stream", "fix.ocr.txt", content));
        assertEquals(2, doc.totalPages());
        assertEquals(PageParseStatus.FAILED, doc.pages().get(0).status());
        assertEquals(PageParseStatus.SUCCESS, doc.pages().get(1).status());
    }

    @Test
    void corruptPdfFails() {
        byte[] tiny = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fake.extract(req("application/pdf", "bad.pdf", tiny)));
        assertTrue(ex.getMessage().contains("损坏") || ex.getMessage().contains("CORRUPT"));
    }

    private static ExtractionRequest req(String type, String name, byte[] content) {
        return new ExtractionRequest(
                "r1", "hash1", ExtractedDocument.SCHEMA_VERSION, type, content, name, "k1");
    }
}
