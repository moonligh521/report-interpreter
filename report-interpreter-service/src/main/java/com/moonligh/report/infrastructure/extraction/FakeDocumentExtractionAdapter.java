package com.moonligh.report.infrastructure.extraction;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.res.BoundingBox;
import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.domain.parse.ExtractedBlock;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractedPage;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.PageParseStatus;

/**
 * 默认 Fake 抽取适配器：扫描/图片夹具返回稳定 ExtractedDocument；损坏输入失败。
 * 由 {@code ExtractionConfig} 装配，避免与端口 Bean 双重注册。
 */
public class FakeDocumentExtractionAdapter implements DocumentExtractionPort {

    public static final String ENGINE = "fake-ocr";
    public static final String ENGINE_VERSION = "1.0.0";

    /** 夹具魔数：内容以该 UTF-8 前缀开头时按夹具解析。 */
    public static final String FIXTURE_PREFIX = "FAKE_OCR_FIXTURE\n";

    @Override
    public ExtractedDocument extract(ExtractionRequest request) {
        byte[] content = request.content();
        if (content == null || content.length == 0) {
            throw fail("EMPTY_CONTENT", "抽取内容为空");
        }
        if (looksCorrupted(content, request.contentType())) {
            throw fail("CORRUPT_INPUT", "损坏或不支持的抽取输入");
        }
        String asText = new String(content, StandardCharsets.UTF_8);
        if (asText.startsWith(FIXTURE_PREFIX)) {
            return parseFixture(asText.substring(FIXTURE_PREFIX.length()));
        }
        // 图片/扫描 PDF：返回稳定的默认 LAB 抽取结果（含页状态）。
        if (isImage(request) || isPdf(request)) {
            return stableScanDocument();
        }
        throw fail("UNKNOWN_INPUT", "未知抽取输入");
    }

    private static ExtractedDocument stableScanDocument() {
        BoundingBox box = new BoundingBox(0.1, 0.2, 0.4, 0.05);
        String line = "K|4.2|mmol/L|3.5|5.5|K 4.2 mmol/L|0|SERUM";
        ExtractedBlock block = new ExtractedBlock("TEXT", line, box, 0.93);
        ExtractedPage page = new ExtractedPage(
                0,
                "REPORT_TYPE=LAB_PANEL\nPATIENT_NAME=Zhang\nPAGE_COUNT=1\n" + line,
                List.of(block),
                List.of(),
                PageParseStatus.SUCCESS,
                0.93);
        return new ExtractedDocument(
                1, List.of(page), 0.92, 0.90, ExtractedDocument.SCHEMA_VERSION, ENGINE_VERSION);
    }

    private static ExtractedDocument parseFixture(String body) {
        List<ExtractedPage> pages = new ArrayList<>();
        String[] pageChunks = body.split("(?m)^---PAGE---\\s*$");
        int index = 0;
        for (String chunk : pageChunks) {
            String text = chunk.strip();
            if (text.isEmpty()) {
                continue;
            }
            PageParseStatus status = PageParseStatus.SUCCESS;
            if (text.contains("STATUS=FAILED")) {
                status = PageParseStatus.FAILED;
            } else if (text.contains("STATUS=LOW_CONFIDENCE")) {
                status = PageParseStatus.LOW_CONFIDENCE;
            } else if (text.contains("STATUS=EMPTY")) {
                status = PageParseStatus.EMPTY;
            } else if (text.contains("STATUS=NEED_MANUAL_REVIEW")) {
                status = PageParseStatus.NEED_MANUAL_REVIEW;
            }
            double conf = status == PageParseStatus.SUCCESS ? 0.93 : 0.40;
            List<ExtractedBlock> blocks = List.of(
                    new ExtractedBlock("TEXT", text, new BoundingBox(0, 0, 1, 1), conf));
            pages.add(new ExtractedPage(index++, text, blocks, List.of(), status, conf));
        }
        if (pages.isEmpty()) {
            throw fail("EMPTY_FIXTURE", "夹具无有效页");
        }
        double ocr = pages.stream().mapToDouble(ExtractedPage::pageConfidence).min().orElse(0.0);
        return new ExtractedDocument(
                pages.size(), List.copyOf(pages), ocr, ocr, ExtractedDocument.SCHEMA_VERSION, ENGINE_VERSION);
    }

    private static boolean looksCorrupted(byte[] content, String contentType) {
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("application/pdf")) {
            // 过短或非 PDF 魔数视为损坏；金标扫描件用夹具前缀或完整 PDF。
            return content.length < 64
                    || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F';
        }
        if (mime.startsWith("image/png")) {
            return content.length < 8 || (content[0] & 0xFF) != 0x89 || content[1] != 'P';
        }
        if (mime.startsWith("image/jpeg") || mime.startsWith("image/jpg")) {
            return content.length < 3 || (content[0] & 0xFF) != 0xFF || (content[1] & 0xFF) != 0xD8;
        }
        // 夹具文本：允许
        String asText = new String(content, StandardCharsets.UTF_8);
        return !asText.startsWith(FIXTURE_PREFIX);
    }

    private static boolean isImage(ExtractionRequest request) {
        String mime = request.contentType() == null ? "" : request.contentType().toLowerCase(Locale.ROOT);
        String name = request.fileName() == null ? "" : request.fileName().toLowerCase(Locale.ROOT);
        return mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static boolean isPdf(ExtractionRequest request) {
        String mime = request.contentType() == null ? "" : request.contentType().toLowerCase(Locale.ROOT);
        String name = request.fileName() == null ? "" : request.fileName().toLowerCase(Locale.ROOT);
        return mime.startsWith("application/pdf") || name.endsWith(".pdf");
    }

    private static BusinessException fail(String detail, String message) {
        return new BusinessException(ErrorCode.REPORT_PARSE_FAILED, message + " [" + detail + "]");
    }
}
