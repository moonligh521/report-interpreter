package com.moonligh.report.domain.parse;

import java.util.List;

/**
 * 外置抽取的单页结果。
 */
public record ExtractedPage(
        int pageIndex,
        String rawText,
        List<ExtractedBlock> blocks,
        List<ExtractedTable> tables,
        PageParseStatus status,
        double pageConfidence) {

    public ExtractedPage {
        rawText = rawText == null ? "" : rawText;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        tables = tables == null ? List.of() : List.copyOf(tables);
        if (status == null) {
            throw new IllegalArgumentException("page status is required");
        }
    }
}
