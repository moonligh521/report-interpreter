package com.moonligh.report.domain.parse;

import java.util.List;

import com.moonligh.report.api.res.BoundingBox;

/**
 * 外置抽取表格。
 */
public record ExtractedTable(
        String tableId,
        BoundingBox bbox,
        List<ExtractedTableCell> cells) {

    public ExtractedTable {
        tableId = tableId == null ? "" : tableId;
        cells = cells == null ? List.of() : List.copyOf(cells);
    }
}
