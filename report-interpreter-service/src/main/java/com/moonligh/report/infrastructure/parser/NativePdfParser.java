package com.moonligh.report.infrastructure.parser;

import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.config.ReportProperties;
import com.moonligh.report.domain.parse.ParseContext;
import com.moonligh.report.domain.parse.ParseResult;
import com.moonligh.report.domain.parse.PdfTextLayerProbe;
import com.moonligh.report.domain.parse.ProbeDecision;
import com.moonligh.report.domain.parse.ProbeReport;
import com.moonligh.report.domain.parse.ReportParser;
import com.moonligh.report.domain.parse.ReportType;

/**
 * 合格文本层 PDF 的 Native 解析器；仅在探针判定 USE_NATIVE 时 supports=true。
 */
@Component
public class NativePdfParser implements ReportParser {

    public static final String PARSER_TYPE = "NATIVE_PDF";
    public static final String PARSER_VERSION = "1.0.0";

    private final PdfTextLayerProbe probe;
    private final NativePdfTextStructurer structurer;
    private final ReportProperties.Limits limits;

    public NativePdfParser(PdfTextLayerProbe probe, NativePdfTextStructurer structurer, ReportProperties properties) {
        this.probe = probe;
        this.structurer = structurer;
        this.limits = properties.getParse().getLimits();
    }

    @Override
    public boolean supports(ParseContext context) {
        if (context.reportType() != ReportType.LAB_PANEL) {
            return false;
        }
        if (!isPdf(context)) {
            return false;
        }
        try {
            ProbeReport report = probe.probe(context.content());
            return report.decision() == ProbeDecision.USE_NATIVE;
        } catch (BusinessException e) {
            return false;
        }
    }

    @Override
    public ParseResult parse(ParseContext context) {
        ProbeReport report = probe.probe(context.content());
        if (report.decision() != ProbeDecision.USE_NATIVE) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "Native 仅处理 USE_NATIVE 路由");
        }
        if (report.totalPages() > limits.getMaxPdfPages()) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "PDF 页数超过上限");
        }
        String text = extractText(context.content());
        return structurer.structure(context.reportType(), text);
    }

    /** 供 Manifest 写入探针结果。 */
    public ProbeReport lastProbe(ParseContext context) {
        return probe.probe(context.content());
    }

    private static boolean isPdf(ParseContext context) {
        String mime = context.contentType() == null ? "" : context.contentType().toLowerCase(Locale.ROOT);
        String name = context.fileName() == null ? "" : context.fileName().toLowerCase(Locale.ROOT);
        return mime.startsWith("application/pdf") || name.endsWith(".pdf");
    }

    private static String extractText(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "PDF 文本提取失败: " + e.getMessage());
        }
    }
}
