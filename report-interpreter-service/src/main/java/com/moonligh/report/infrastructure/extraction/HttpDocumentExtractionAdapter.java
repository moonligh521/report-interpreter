package com.moonligh.report.infrastructure.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.BoundingBox;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.domain.parse.ExtractedBlock;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractedPage;
import com.moonligh.report.domain.parse.ExtractedTable;
import com.moonligh.report.domain.parse.ExtractedTableCell;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.PageParseStatus;

/**
 * 同步 HTTP 文档抽取适配器（超时 / 5xx / 坏 JSON → 4102）。不做异步回调。
 * 由 {@code ExtractionConfig} 装配。
 */
public class HttpDocumentExtractionAdapter implements DocumentExtractionPort {

    public static final String ENGINE = "http-ocr";

    private final HttpClient httpClient;
    private final ReportProperties.Extraction config;
    private final ObjectMapper json = new ObjectMapper();

    public HttpDocumentExtractionAdapter(HttpClient extractionHttpClient, ReportProperties properties) {
        this.httpClient = extractionHttpClient;
        this.config = properties.getParse().getExtraction();
    }

    @Override
    public ExtractedDocument extract(ExtractionRequest request) {
        if (request.content().length > config.getMaxRequestBytes()) {
            throw fail("REQUEST_TOO_LARGE", "抽取请求体超过上限");
        }
        try {
            ExtractionHttpRequest body = new ExtractionHttpRequest(
                    request.requestId(),
                    request.documentHash(),
                    request.schemaVersion(),
                    request.contentType(),
                    Base64.getEncoder().encodeToString(request.content()),
                    request.fileName(),
                    request.idempotencyKey());
            byte[] payload = json.writeValueAsBytes(body);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(config.getBaseUrl()) + "/v1/extract"))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", request.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 500) {
                throw fail("HTTP_" + response.statusCode(), "抽取服务不可用");
            }
            if (response.statusCode() >= 400) {
                throw fail("HTTP_" + response.statusCode(), "抽取请求被拒绝");
            }
            byte[] responseBytes = response.body().readNBytes((int) Math.min(Integer.MAX_VALUE, config.getMaxResponseBytes() + 1));
            if (responseBytes.length > config.getMaxResponseBytes()) {
                throw fail("RESPONSE_TOO_LARGE", "抽取响应体超过上限");
            }
            ExtractionHttpResponse parsed;
            try {
                parsed = json.readValue(responseBytes, ExtractionHttpResponse.class);
            } catch (IOException e) {
                throw fail("BAD_JSON", "抽取响应 JSON 不合法");
            }
            return toDocument(parsed);
        } catch (BusinessException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw fail("TIMEOUT", "抽取超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail("INTERRUPTED", "抽取被中断");
        } catch (IOException e) {
            throw fail("IO_ERROR", "抽取 IO 失败: " + e.getMessage());
        }
    }

    private ExtractedDocument toDocument(ExtractionHttpResponse parsed) {
        if (parsed == null) {
            throw fail("EMPTY_RESPONSE", "抽取响应为空");
        }
        if (parsed.schemaVersion() == null || parsed.schemaVersion().isBlank()) {
            throw fail("CONTRACT", "缺少 schemaVersion");
        }
        if (parsed.processingStatus() == null || parsed.processingStatus().isBlank()) {
            throw fail("CONTRACT", "缺少 processingStatus");
        }
        if (!"SUCCEEDED".equalsIgnoreCase(parsed.processingStatus())) {
            String code = parsed.errorCode() == null ? "ENGINE_FAILED" : parsed.errorCode();
            throw fail(code, "抽取未成功: " + parsed.processingStatus());
        }
        if (parsed.pages() == null || parsed.pages().isEmpty()) {
            throw fail("CONTRACT", "缺少 pages");
        }
        List<ExtractedPage> pages = new ArrayList<>();
        for (ExtractionHttpPage p : parsed.pages()) {
            PageParseStatus status = parseStatus(p.status());
            List<ExtractedBlock> blocks = new ArrayList<>();
            if (p.blocks() != null) {
                for (ExtractionHttpBlock b : p.blocks()) {
                    blocks.add(new ExtractedBlock(
                            b.blockType(), b.text(), toBox(b.bbox()), b.confidence()));
                }
            }
            List<ExtractedTable> tables = new ArrayList<>();
            if (p.tables() != null) {
                for (ExtractionHttpTable t : p.tables()) {
                    List<ExtractedTableCell> cells = new ArrayList<>();
                    if (t.cells() != null) {
                        for (ExtractionHttpCell c : t.cells()) {
                            cells.add(new ExtractedTableCell(c.rowIndex(), c.columnIndex(), c.text(), toBox(c.bbox())));
                        }
                    }
                    tables.add(new ExtractedTable(t.tableId(), toBox(t.bbox()), cells));
                }
            }
            pages.add(new ExtractedPage(
                    p.pageIndex(), p.rawText(), blocks, tables, status, p.pageConfidence()));
        }
        String engineVersion = parsed.engineVersion() == null ? "" : parsed.engineVersion();
        return new ExtractedDocument(
                parsed.pageCount() > 0 ? parsed.pageCount() : pages.size(),
                pages,
                parsed.ocrConfidence(),
                parsed.layoutConfidence(),
                parsed.schemaVersion(),
                engineVersion);
    }

    private static PageParseStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return PageParseStatus.SUCCESS;
        }
        try {
            return PageParseStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw fail("CONTRACT", "非法页状态: " + raw);
        }
    }

    private static BoundingBox toBox(ExtractionHttpBBox b) {
        if (b == null) {
            return null;
        }
        return new BoundingBox(b.x(), b.y(), b.width(), b.height());
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "http://127.0.0.1:18093";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static BusinessException fail(String detail, String message) {
        return new BusinessException(ErrorCode.REPORT_PARSE_FAILED, message + " [" + detail + "]");
    }

    public record ExtractionHttpRequest(
            String requestId,
            String documentHash,
            String schemaVersion,
            String contentType,
            String contentBase64,
            String fileName,
            String idempotencyKey) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpResponse(
            String requestId,
            String schemaVersion,
            String processingStatus,
            int pageCount,
            List<ExtractionHttpPage> pages,
            double ocrConfidence,
            double layoutConfidence,
            String engineVersion,
            String errorCode,
            String serverTaskId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpPage(
            int pageIndex,
            String rawText,
            String status,
            double pageConfidence,
            List<ExtractionHttpBlock> blocks,
            List<ExtractionHttpTable> tables) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpBlock(
            String blockType, String text, ExtractionHttpBBox bbox, double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpTable(
            String tableId, ExtractionHttpBBox bbox, List<ExtractionHttpCell> cells) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpCell(
            int rowIndex, int columnIndex, String text, ExtractionHttpBBox bbox) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractionHttpBBox(double x, double y, double width, double height) {
    }
}
