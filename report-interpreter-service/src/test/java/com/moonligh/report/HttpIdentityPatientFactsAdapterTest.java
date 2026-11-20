package com.moonligh.report;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.rule.FactsSource;
import com.moonligh.report.domain.rule.PatientFactsResolution;
import com.moonligh.report.infrastructure.facts.HttpIdentityPatientFactsAdapter;

/** CF-3：身份 HTTP 200 / 5xx / 超时 / 坏 JSON → UNKNOWN 或完整 Facts。 */
class HttpIdentityPatientFactsAdapterTest {

    private WireMockServer server;
    private HttpIdentityPatientFactsAdapter adapter;
    private final Instant asOf = Instant.parse("2026-11-20T00:00:00Z");

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        ReportProperties props = new ReportProperties();
        props.getClinicalFacts().getIdentity().setBaseUrl("http://127.0.0.1:" + server.port());
        props.getClinicalFacts().getIdentity().setConnectTimeoutMs(2_000);
        props.getClinicalFacts().getIdentity().setReadTimeoutMs(1_000);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        adapter = new HttpIdentityPatientFactsAdapter(client, props);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void success200MapsAgeAndSex() {
        server.stubFor(get(urlEqualTo("/v1/patients/p-1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"birthDate":"1984-11-20","sex":"M","pregnancyStatus":null}
                        """)));
        PatientFactsResolution r = adapter.resolve("p-1", asOf);
        assertEquals(FactsSource.IDENTITY, r.source());
        assertFalse(r.incomplete());
        assertEquals(42, r.facts().age());
        assertEquals("M", r.facts().sex());
    }

    @Test
    void http5xxYieldsIdentityFailedUnknown() {
        server.stubFor(get(urlEqualTo("/v1/patients/p-1")).willReturn(aResponse().withStatus(503)));
        PatientFactsResolution r = adapter.resolve("p-1", asOf);
        assertEquals(FactsSource.IDENTITY_FAILED, r.source());
        assertTrue(r.incomplete());
        assertEquals("HTTP_503", r.detailCode());
    }

    @Test
    void timeoutYieldsIdentityFailedUnknown() {
        server.stubFor(get(urlEqualTo("/v1/patients/p-1")).willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(2_500)
                .withBody("{}")));
        PatientFactsResolution r = adapter.resolve("p-1", asOf);
        assertEquals(FactsSource.IDENTITY_FAILED, r.source());
        assertEquals("TIMEOUT", r.detailCode());
    }

    @Test
    void badJsonYieldsIdentityFailedUnknown() {
        server.stubFor(get(urlEqualTo("/v1/patients/p-1")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("not-json")));
        PatientFactsResolution r = adapter.resolve("p-1", asOf);
        assertEquals(FactsSource.IDENTITY_FAILED, r.source());
        assertEquals("BAD_JSON", r.detailCode());
    }
}
