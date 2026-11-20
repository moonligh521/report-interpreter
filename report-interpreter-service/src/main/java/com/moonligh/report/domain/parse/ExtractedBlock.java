package com.moonligh.report.domain.parse;

import com.moonligh.report.api.res.BoundingBox;

/**
 * 外置抽取的文本块（版面单元）。
 */
public record ExtractedBlock(
        String blockType,
        String text,
        BoundingBox bbox,
        double confidence) {

    public ExtractedBlock {
        blockType = blockType == null ? "" : blockType;
        text = text == null ? "" : text;
    }
}
