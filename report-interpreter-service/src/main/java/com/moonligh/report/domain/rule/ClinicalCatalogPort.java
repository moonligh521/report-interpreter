package com.moonligh.report.domain.rule;

/**
 * 临床目录端口。版本不存在时应明确失败，禁止静默回退错表。
 */
public interface ClinicalCatalogPort {

    ClinicalCatalog load(String version);
}
