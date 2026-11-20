package com.moonligh.report.domain.parse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.config.ReportProperties;

/**
 * PDF 文本层可用性探针：可解释路由，非整页「有字即合格」。
 */
@Component
public class PdfTextLayerProbe {

    private final ReportProperties.Probe thresholds;

    public PdfTextLayerProbe(ReportProperties properties) {
        this.thresholds = properties.getParse().getProbe();
    }

    public ProbeReport probe(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "PDF 内容为空");
        }
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            if (totalPages <= 0) {
                return failOpenReview(0, "PDF 无有效页面");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(doc);
                pages.add(pageText == null ? "" : pageText);
            }
            return evaluate(pages);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.REPORT_PARSE_FAILED, "PDF 文本层探测失败: " + e.getMessage());
        }
    }

    /** 按页文本评估（单测可注入乱码/过稀场景，不依赖字体编码）。 */
    public ProbeReport evaluate(List<String> pageTexts) {
        if (pageTexts == null || pageTexts.isEmpty()) {
            return failOpenReview(0, "PDF 无有效页面");
        }
        int totalPages = pageTexts.size();
        int nonEmpty = 0;
        int totalChars = 0;
        int extractable = 0;
        int considered = 0;
        for (String pageText : pageTexts) {
            String normalized = pageText == null ? "" : pageText.strip();
            if (!normalized.isEmpty()) {
                nonEmpty++;
            }
            totalChars += normalized.length();
            for (int c = 0; c < normalized.length(); c++) {
                char ch = normalized.charAt(c);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                considered++;
                if (isExtractable(ch)) {
                    extractable++;
                }
            }
        }
        double nonEmptyRatio = (double) nonEmpty / totalPages;
        double extractableRatio = considered == 0 ? 0.0 : (double) extractable / considered;
        double avgChars = (double) totalChars / totalPages;

        boolean nonEmptyOk = nonEmptyRatio >= thresholds.getMinNonEmptyPageRatio();
        boolean extractableOk = extractableRatio >= thresholds.getMinExtractableCharRatio();
        boolean densityOk = avgChars >= thresholds.getMinAverageCharsPerPage();
        boolean totalOk = totalChars >= thresholds.getMinTotalChars();

        List<String> reasons = new ArrayList<>();
        ProbeDecision decision;
        if (!nonEmptyOk || !extractableOk || !totalOk) {
            decision = ProbeDecision.USE_OCR;
            if (!nonEmptyOk) {
                reasons.add("非空页比例过低: " + format(nonEmptyRatio));
            }
            if (!extractableOk) {
                reasons.add("可提取字符比例过低(疑似乱码): " + format(extractableRatio));
            }
            if (!totalOk) {
                reasons.add("总字符数过低: " + totalChars);
            }
        } else if (!densityOk) {
            decision = ProbeDecision.NEED_REVIEW;
            reasons.add("页面文本密度过低: " + format(avgChars));
        } else {
            decision = ProbeDecision.USE_NATIVE;
            reasons.add("文本层指标达标，走 Native");
        }
        return new ProbeReport(
                decision, totalPages, nonEmpty, nonEmptyRatio, extractableRatio, avgChars, totalChars,
                nonEmptyOk, extractableOk, densityOk, totalOk, reasons);
    }

    private static ProbeReport failOpenReview(int pages, String reason) {
        return new ProbeReport(
                ProbeDecision.NEED_REVIEW, pages, 0, 0, 0, 0, 0,
                false, false, false, false, List.of(reason));
    }

    private static boolean isExtractable(char ch) {
        if (ch == '\uFFFD' || Character.isISOControl(ch)) {
            return false;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        if (block == Character.UnicodeBlock.SPECIALS) {
            return false;
        }
        return Character.isLetterOrDigit(ch)
                || Character.getType(ch) == Character.OTHER_LETTER
                || isPunctuationOrSymbol(ch);
    }

    private static boolean isPunctuationOrSymbol(char ch) {
        int type = Character.getType(ch);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || ch == '|' || ch == '=' || ch == '.' || ch == ',' || ch == ':' || ch == ';';
    }

    private static String format(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
