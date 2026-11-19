package com.moonligh.report.domain.parse;

import java.util.List;
import java.util.Optional;

/**
 * 解析器注册表：按 supports 选出唯一解析器，不做失败后的串行回退。
 */
public class ParserRegistry {

    private final List<ReportParser> parsers;

    public ParserRegistry(List<ReportParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public Optional<ReportParser> find(ParseContext context) {
        return parsers.stream().filter(p -> p.supports(context)).findFirst();
    }

    public int size() {
        return parsers.size();
    }
}