package com.moonligh.report.domain.parse;

import com.moonligh.report.api.res.BoundingBox;

/**
 * 外置抽取表格单元格。
 */
public record ExtractedTableCell(
        int rowIndex,
        int columnIndex,
        String text,
        BoundingBox bbox) {

    public ExtractedTableCell {
        text = text == null ? "" : text;
    }
}
