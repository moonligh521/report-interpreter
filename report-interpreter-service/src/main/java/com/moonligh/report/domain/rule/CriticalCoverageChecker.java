package com.moonligh.report.domain.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.moonligh.report.api.res.CriticalCoverageEvidence;
import com.moonligh.report.domain.parse.ParsedIndicator;
import com.moonligh.report.domain.parse.ReportMeta;

/**
 * 可验证的危急覆盖判定（证据驱动，不信任「模型声称完成」）。
 * <ul>
 *   <li>UNKNOWN：没有任何已处理页；或声明了总页数但存在既未处理也未报失败的页</li>
 *   <li>PARTIAL：有失败页 / 无法归属或无法规范化的指标 / 未识别文本区</li>
 *   <li>COMPLETE：以上皆无，且每个已处理页都有可回溯的指标证据</li>
 * </ul>
 * 未声明总页数时（金标文本夹具），以指标出现的页为已处理页；真实 OCR 解析器必须声明总页数。
 */
public final class CriticalCoverageChecker {

    public CriticalCoverageResult check(List<ParsedIndicator> indicators, ReportMeta meta) {
        ReportMeta m = meta == null ? ReportMeta.EMPTY : meta;
        List<Integer> processed = processedPages(indicators);
        List<Integer> failed = m.failedPages().stream().distinct().sorted().toList();
        List<String> unmapped = unmappedFlags(indicators);
        CriticalCoverageEvidence evidence = new CriticalCoverageEvidence(
                processed, failed, unmapped, m.unrecognizedRegions());

        if (processed.isEmpty() || hasUnaccountedPage(m.totalPages(), processed, failed)) {
            return new CriticalCoverageResult(CoverageStatus.UNKNOWN, evidence);
        }
        if (!failed.isEmpty() || !unmapped.isEmpty() || !m.unrecognizedRegions().isEmpty()) {
            return new CriticalCoverageResult(CoverageStatus.PARTIAL, evidence);
        }
        return new CriticalCoverageResult(CoverageStatus.COMPLETE, evidence);
    }

    private static List<Integer> processedPages(List<ParsedIndicator> indicators) {
        TreeSet<Integer> pages = new TreeSet<>();
        for (ParsedIndicator ind : indicators) {
            if (ind.evidence() != null && ind.evidence().pageIndex() != null) {
                pages.add(ind.evidence().pageIndex());
            }
        }
        return List.copyOf(pages);
    }

    /** 无法归属到规范指标、或数值/单位无法规范化的项：可能是被漏掉的危急项。 */
    private static List<String> unmappedFlags(List<ParsedIndicator> indicators) {
        List<String> flags = new ArrayList<>();
        for (ParsedIndicator ind : indicators) {
            if (ind.canonicalCode() == null || ind.normalizedValue() == null) {
                flags.add(ind.rawName());
            }
        }
        return flags;
    }

    private static boolean hasUnaccountedPage(Integer totalPages, List<Integer> processed, List<Integer> failed) {
        if (totalPages == null) {
            return false;
        }
        for (int page = 0; page < totalPages; page++) {
            if (!processed.contains(page) && !failed.contains(page)) {
                return true;
            }
        }
        return false;
    }
}
