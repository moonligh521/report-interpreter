package com.moonligh.report;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.PageParseStatus;
import com.moonligh.report.infrastructure.extraction.HttpDocumentExtractionAdapter;

/** Task7：HTTP 抽取 200 / 5xx / 超时 / 坏 JSON。 */
class HttpDocumentExtractionAdapterTest {

    private WireMockServer server;
    private HttpDocumentExtractionAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        ReportProperties props = new ReportProperties();
        props.getParse().getExtraction().setBaseUrl("http://127.0.0.1:" + server.port());
        props.getParse().getExtraction().setConnectTimeoutMs(2_000);
        props.getParse().getExtraction().setReadTimeoutMs(1_000);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        adapter = new HttpDocumentExtractionAdapter(client, props);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void success200MapsTypedDocument() {
        server.stubFor(post(urlEqualTo("/v1/extract")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "requestId":"r1",
                          "schemaVersion":"1.0",
                          "processingStatus":"SUCCEEDED",
                          "pageCount":1,
                          "ocrConfidence":0.91,
                          "layoutConfidence":0.88,
                          "engineVersion":"paddle-mock-1",
                          "pages":[{
                            "pageIndex":0,
                            "rawText":"REPORT_TYPE=LAB_PANEL\\nK|4.2|mmol/L|3.5|5.5|K 4.2|0|SERUM",
                            "status":"SUCCESS",
                            "pageConfidence":0.9,
                            "blocks":[{"blockType":"TEXT","text":"K|4.2|mmol/L","bbox":{"x":0.1,"y":0.2,"width":0.3,"height":0.05},"confidence":0.9}],
                            "tables":[]
                          }]
                        }
                        """)));
        ExtractedDocument doc = adapter.extract(sampleRequest());
        assertEquals(1, doc.totalPages());
        assertEquals(PageParseStatus.SUCCESS, doc.pages().get(0).status());
        assertEquals("1.0", doc.schemaVersion());
        assertEquals("paddle-mock-1", doc.engineVersion());
        server.verify(postRequestedFor(urlEqualTo("/v1/extract"))
                .withHeader("Idempotency-Key", equalTo("k1")));
    }

    @Test
    void http5xxMapsTo4102() {
        server.stubFor(post(urlEqualTo("/v1/extract")).willReturn(aResponse().withStatus(503)));
        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.extract(sampleRequest()));
        assertEquals(4102, ex.getCode());
        assertTrue(ex.getMessage().contains("503") || ex.getMessage().contains("不可用"));
    }

    @Test
    void timeoutMapsTo4102() {
        server.stubFor(post(urlEqualTo("/v1/extract")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2500)
                .withBody("{}")));
        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.extract(sampleRequest()));
        assertEquals(4102, ex.getCode());
        assertTrue(ex.getMessage().contains("超时") || ex.getMessage().contains("TIMEOUT")
                || ex.getMessage().contains("IO"));
    }

    @Test
    void badJsonMapsTo4102() {
        server.stubFor(post(urlEqualTo("/v1/extract")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{not-json")));
        BusinessException ex = assertThrows(BusinessException.class, () -> adapter.extract(sampleRequest()));
        assertEquals(4102, ex.getCode());
        assertTrue(ex.getMessage().contains("JSON") || ex.getMessage().contains("BAD_JSON"));
    }

    private static ExtractionRequest sampleRequest() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
        return new ExtractionRequest(
                "r1", "hash1", ExtractedDocument.SCHEMA_VERSION, "image/png", png, "a.png", "k1");
    }
}
