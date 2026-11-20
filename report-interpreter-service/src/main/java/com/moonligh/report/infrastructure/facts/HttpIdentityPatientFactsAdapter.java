package com.moonligh.report.infrastructure.facts;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.rule.FactsSource;
import com.moonligh.report.domain.rule.PatientFacts;
import com.moonligh.report.domain.rule.PatientFactsPort;
import com.moonligh.report.domain.rule.PatientFactsResolution;

/**
 * 身份服务 HTTP 人口学适配。超时 / 4xx / 5xx / 坏 JSON → UNKNOWN + IDENTITY_FAILED，不抛解析域错误。
 */
public final class HttpIdentityPatientFactsAdapter implements PatientFactsPort {

    private final HttpClient httpClient;
    private final ReportProperties.Identity config;
    private final ObjectMapper json = new ObjectMapper();

    public HttpIdentityPatientFactsAdapter(HttpClient identityHttpClient, ReportProperties properties) {
        this.httpClient = identityHttpClient;
        this.config = properties.getClinicalFacts().getIdentity();
    }

    @Override
    public PatientFactsResolution resolve(String patientId, java.time.Instant asOf) {
        if (patientId == null || patientId.isBlank()) {
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "MISSING_PATIENT_ID");
        }
        String base = config.getBaseUrl();
        if (base == null || base.isBlank()) {
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "MISSING_BASE_URL");
        }
        try {
            String encoded = URLEncoder.encode(patientId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(base) + "/v1/patients/" + encoded))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 500) {
                return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "HTTP_" + response.statusCode());
            }
            if (response.statusCode() >= 400) {
                return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "HTTP_" + response.statusCode());
            }
            IdentityPatientResponse body;
            try {
                body = json.readValue(response.body(), IdentityPatientResponse.class);
            } catch (IOException e) {
                return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "BAD_JSON");
            }
            return toResolution(body, asOf);
        } catch (java.net.http.HttpTimeoutException e) {
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "INTERRUPTED");
        } catch (IOException e) {
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "IO_ERROR");
        }
    }

    private static PatientFactsResolution toResolution(IdentityPatientResponse body, java.time.Instant asOf) {
        if (body == null) {
            return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "EMPTY_RESPONSE");
        }
        Integer age = null;
        if (body.birthDate() != null && !body.birthDate().isBlank()) {
            try {
                LocalDate birth = LocalDate.parse(body.birthDate());
                LocalDate on = asOf == null
                        ? LocalDate.now(ZoneOffset.UTC)
                        : LocalDate.ofInstant(asOf, ZoneOffset.UTC);
                age = Period.between(birth, on).getYears();
            } catch (DateTimeParseException e) {
                return PatientFactsResolution.unknown(FactsSource.IDENTITY_FAILED, "BAD_BIRTH_DATE");
            }
        }
        String sex = body.sex() == null || body.sex().isBlank() ? null : body.sex().trim();
        PatientFacts facts = new PatientFacts(age, sex, body.pregnancyStatus());
        boolean incomplete = age == null || sex == null;
        if (incomplete) {
            return new PatientFactsResolution(facts, FactsSource.IDENTITY, true, "INCOMPLETE_DEMOGRAPHICS");
        }
        return new PatientFactsResolution(facts, FactsSource.IDENTITY, false, null);
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdentityPatientResponse(String birthDate, String sex, String pregnancyStatus) {
    }
}
