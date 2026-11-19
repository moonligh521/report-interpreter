package com.moonligh.report.domain.interpret;

/**
 * 解读钉死版本里由本服务代码决定的三项（kb / rule 由数据决定）。
 * 任一项升级都必须递增，历史解读保留当时版本。
 */
public final class InterpretationVersions {

    public static final String PROMPT_VERSION = "prompt-lab-v1";
    public static final String PARSER_VERSION = "parser-lab-text-v1";
    public static final String SCHEMA_VERSION = "interp-schema-v1";

    /** 知识库为空时的 kbVersion 占位。 */
    public static final String KB_EMPTY = "KB_EMPTY";
    /** 知识库读取失败时的 kbVersion 占位。 */
    public static final String KB_UNAVAILABLE = "KB_UNAVAILABLE";

    private InterpretationVersions() {
    }
}
