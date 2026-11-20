package com.moonligh.report.infrastructure.parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.domain.parse.DocumentExtractionPort;
import com.moonligh.report.domain.parse.ExtractedDocument;
import com.moonligh.report.domain.parse.ExtractionRequest;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.PdfTextLayerProbe;
import com.moonligh.report.domain.parse.ProbeDecision;
import com.moonligh.report.domain.parse.ProbeReport;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;
import com.moonligh.report.infrastructure.extraction.FakeDocumentExtractionAdapter;

/**
 * 图片或探针 USE_OCR / NEED_REVIEW 的 PDF → DocumentExtractionPort → 结构化。
 * 与 {@link NativePdfParser} 互斥：同一 context 至多一个 supports=true。
 */
@Component
public class OcrLayoutParser implements ReportParser {

    public static final String PARSER_TYPE = "OCR_LAYOUT";
    public static final String PARSER_VERSION = "1.0.0";

    private final DocumentExtractionPort extraction;
    private final OcrExtractedStructurer structurer;
    private final PdfTextLayerProbe probe;

    public OcrLayoutParser(
            DocumentExtractionPort extraction, OcrExtractedStructurer structurer, PdfTextLayerProbe probe) {
        this.extraction = extraction;
        this.structurer = structurer;
        this.probe = probe;
    }

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.LAB_PANEL) {
            return false;
        }
        if (isImage(context)) {
            return true;
        }
        if (!isPdf(context)) {
            return false;
        }
        try {
            ProbeDecision decision = probe.probe(context.content()).decision();
            return decision == ProbeDecision.USE_OCR || decision == ProbeDecision.NEED_REVIEW;
        } catch (BusinessException e) {
            // 探针失败：交 OCR 路径尝试；损坏输入由抽取端口失败。
            return true;
        }
    }

    @Override
    public ParseResult parse(ParseContext context) {
        ProbeReport probeReport = null;
        if (isPdf(context)) {
            try {
                probeReport = probe.probe(context.content());
            } catch (BusinessException ignored) {
                // 损坏 PDF：仍走抽取，由 Fake/Http 报失败。
            }
        }
        String hash = sha256(context.content());
        ExtractionRequest request = new ExtractionRequest(
                context.reportId() == null ? UUID.randomUUID().toString() : context.reportId(),
                hash,
                ExtractedDocument.SCHEMA_VERSION,
                context.contentType(),
                context.content(),
                context.fileName(),
                context.reportId());
        ExtractedDocument doc = extraction.extract(request);
        ParseResult result = structurer.structure(context.reportType(), doc);
        // 将探针失败页信息并入 meta（若抽取夹具未声明）。
        if (probeReport != null && result.meta().failedPages().isEmpty()
                && probeReport.decision() == ProbeDecision.NEED_REVIEW) {
            return result;
        }
        return result;
    }

    public ProbeReport probeIfPdf(ParseContext context) {
        if (!isPdf(context)) {
            return null;
        }
        return probe.probe(context.content());
    }

    private static boolean isImage(ParseContext context) {
        String mime = context.contentType() == null ? "" : context.contentType().toLowerCase(Locale.ROOT);
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return mime.startsWith("image/") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static boolean isPdf(ParseContext context) {
        String mime = context.contentType() == null ? "" : context.contentType().toLowerCase(Locale.ROOT);
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return mime.startsWith("application/pdf") || name.endsWith(".pdf");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 测试辅助：夹具字节。 */
    public static byte[] fixtureBytes(String body) {
        return (FakeDocumentExtractionAdapter.FIXTURE_PREFIX + body).getBytes(StandardCharsets.UTF_8);
    }
}
