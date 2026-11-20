package com.moonligh.report.domain.parse;

/**
 * 外置文档抽取端口（Fake / Http）。领域只依赖本接口，不绑定具体 OCR 引擎。
 */
public interface DocumentExtractionPort {

    ExtractedDocument extract(ExtractionRequest request);
}
