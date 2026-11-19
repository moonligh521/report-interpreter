package com.moonligh.report.domain.kb;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 混合召回：结构化过滤（indicatorCode / specialty，下推存储层）为主，模糊词关键词重排为辅。
 *
 * <p>fail-closed 不变式（对齐菜品推荐检索 §5/§6）：
 * <ul>
 *   <li>有强制过滤时，存储异常 / 过滤后为空 / 关键词无命中 ⇒ 返回空列表 + 空因，绝不退回全库；</li>
 *   <li>存储层返回的分片还要经 {@link RecallFilter#accepts} 二次校验，存储层漏过滤也不会外泄；</li>
 *   <li>{@link #intersect} 的 ids 为空 ⇒ 空列表，调用方想要「全库」必须显式走无过滤路径。</li>
 * </ul>
 * 不用向量猜指标类型：模糊词只在已过滤候选内做关键词打分。
 */
public class HybridRecall {

    private final KbChunkStore store;
    private final Clock clock;

    public HybridRecall(KbChunkStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public List<KnowledgeChunk> search(RecallQuery query, RecallFilter filter) {
        return searchWithReport(query, filter).chunks();
    }

    public RecallResult searchWithReport(RecallQuery query, RecallFilter filter) {
        RecallQuery q = query == null ? RecallQuery.byFilterOnly() : query;
        RecallFilter f = filter == null ? RecallFilter.NONE : filter;
        String expr = f.expr();

        List<KnowledgeChunk> candidates;
        try {
            candidates = store.listPublished(clock.instant(), f.indicatorCode(), f.specialty());
        } catch (RuntimeException e) {
            return RecallResult.empty(RecallEmptyReason.STORE_UNAVAILABLE, expr);
        }
        if (candidates == null) {
            return RecallResult.empty(RecallEmptyReason.STORE_UNAVAILABLE, expr);
        }
        List<KnowledgeChunk> filtered = candidates.stream().filter(f::accepts).toList();
        if (filtered.isEmpty()) {
            return RecallResult.empty(
                    f.hasFilter() ? RecallEmptyReason.SCALAR_EMPTY : RecallEmptyReason.NO_PUBLISHED_INTERSECT, expr);
        }

        List<KnowledgeChunk> ranked;
        if (q.fuzzy()) {
            List<Scored> scored = new ArrayList<>();
            for (KnowledgeChunk c : filtered) {
                int score = keywordScore(q.text(), c);
                if (score > 0) {
                    scored.add(new Scored(c, score));
                }
            }
            Set<String> hitIds = new HashSet<>();
            scored.forEach(s -> hitIds.add(s.chunk().chunkId()));
            List<KnowledgeChunk> hit = intersect(filtered, hitIds);
            if (hit.isEmpty()) {
                return RecallResult.empty(RecallEmptyReason.KEYWORD_EMPTY, expr);
            }
            scored.sort(Comparator
                    .comparingInt((Scored s) -> -s.score())
                    .thenComparing(s -> s.chunk().chunkId()));
            ranked = scored.stream().map(Scored::chunk).toList();
        } else {
            ranked = new ArrayList<>(filtered);
            ranked.sort(Comparator.comparing(KnowledgeChunk::chunkId));
        }
        List<KnowledgeChunk> top = ranked.stream().limit(q.effectiveLimit()).toList();
        return new RecallResult(top, RecallEmptyReason.NONE, top.size(), expr);
    }

    /**
     * 已发布目录与召回 id 取交集。ids 为空 ⇒ 空列表（不是全库）；交集为空 ⇒ 空列表。
     */
    public static List<KnowledgeChunk> intersect(List<KnowledgeChunk> published, Set<String> recalledIds) {
        if (published == null || published.isEmpty() || recalledIds == null || recalledIds.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new HashSet<>(recalledIds);
        return published.stream().filter(c -> ids.contains(c.chunkId())).toList();
    }

    private record Scored(KnowledgeChunk chunk, int score) {
    }

    /** 关键词打分：ASCII 词整词匹配 + 中文二元组匹配。 */
    static int keywordScore(String text, KnowledgeChunk chunk) {
        String haystack = ((chunk.title() == null ? "" : chunk.title()) + " "
                + (chunk.content() == null ? "" : chunk.content())).toLowerCase();
        int score = 0;
        for (String token : tokens(text)) {
            if (haystack.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static List<String> tokens(String text) {
        String t = text.toLowerCase().strip();
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        List<Character> han = new ArrayList<>();
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (Character.isLetterOrDigit(ch) && ch < 128) {
                flushHan(han, out);
                word.append(ch);
            } else if (Character.isLetterOrDigit(ch)) {
                if (word.length() > 0) {
                    out.add(word.toString());
                    word.setLength(0);
                }
                han.add(ch);
            } else {
                if (word.length() > 0) {
                    out.add(word.toString());
                    word.setLength(0);
                }
                flushHan(han, out);
            }
        }
        if (word.length() > 0) {
            out.add(word.toString());
        }
        flushHan(han, out);
        return out;
    }

    private static void flushHan(List<Character> han, List<String> out) {
        if (han.size() == 1) {
            out.add(String.valueOf(han.get(0)));
        }
        for (int i = 0; i + 1 < han.size(); i++) {
            out.add("" + han.get(i) + han.get(i + 1));
        }
        han.clear();
    }
}
