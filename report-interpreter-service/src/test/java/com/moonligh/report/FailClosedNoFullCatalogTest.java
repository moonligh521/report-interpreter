package com.moonligh.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.moonligh.report.domain.kb.HybridRecall;
import com.moonligh.report.domain.kb.KbChunkStore;
import com.moonligh.report.domain.kb.KnowledgeChunk;
import com.moonligh.report.domain.kb.RecallEmptyReason;
import com.moonligh.report.domain.kb.RecallFilter;
import com.moonligh.report.domain.kb.RecallQuery;
import com.moonligh.report.domain.kb.RecallResult;

/** AC-G9 / G-R8 / G-R9：有强制过滤时召回为空或失败 → 空列表 + 空因，绝不退回全库（对齐菜品检索 §5/§6）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FailClosedNoFullCatalogTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC);

    private static final KnowledgeChunk K1 = chunk("c-k-1", "K", "LAB", "血钾高于参考范围需要医生评估");
    private static final KnowledgeChunk K2 = chunk("c-k-2", "K", "LAB", "血钾低于参考范围需要复查");
    private static final KnowledgeChunk GLU = chunk("c-glu-1", "GLU", "LAB", "血糖高于参考范围需要综合评估");
    private static final KnowledgeChunk IMG = chunk("c-img-1", "K", "IMAGING", "影像科资料不应被检验解读召回");
    private static final List<KnowledgeChunk> CATALOG = List.of(K1, K2, GLU, IMG);

    @Autowired
    private HybridRecall springRecall;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static KnowledgeChunk chunk(String id, String code, String specialty, String content) {
        return new KnowledgeChunk(id, "doc-" + id, "标题-" + id, content, code, specialty, null, "kb-t", "A");
    }

    /** 测试存储：可配置为「无视过滤返回全目录」或「抛异常」，用来证明 HybridRecall 自己 fail-closed。 */
    private static KbChunkStore store(boolean ignoreFilter, boolean failing) {
        return new KbChunkStore() {
            @Override
            public List<KnowledgeChunk> listPublished(Instant now, String indicatorCode, String specialty) {
                if (failing) {
                    throw new IllegalStateException("store down");
                }
                if (ignoreFilter) {
                    return CATALOG;
                }
                return CATALOG.stream()
                        .filter(c -> indicatorCode == null || indicatorCode.equals(c.indicatorCode()))
                        .filter(c -> specialty == null || specialty.equals(c.specialty()))
                        .toList();
            }

            @Override
            public Optional<String> currentKbVersion(Instant now) {
                return Optional.of("kb-t");
            }
        };
    }

    private static HybridRecall recall(boolean ignoreFilter, boolean failing) {
        return new HybridRecall(store(ignoreFilter, failing), CLOCK);
    }

    @Test
    void structuredFilterReturnsOnlyMatchingChunks() {
        List<KnowledgeChunk> out = recall(false, false).search(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "LAB"));
        assertEquals(List.of("c-k-1", "c-k-2"), out.stream().map(KnowledgeChunk::chunkId).toList());
    }

    @Test
    void forcedFilterWithNoMatchReturnsEmptyNotTheCatalog() {
        RecallResult r = recall(false, false).searchWithReport(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("NOPE", "LAB"));
        assertTrue(r.chunks().isEmpty());
        assertEquals(RecallEmptyReason.SCALAR_EMPTY, r.emptyReason());
        assertEquals(0, r.recalledCount());
        assertEquals("indicatorCode==NOPE && specialty==LAB", r.filterExpr());
    }

    @Test
    void storeThatIgnoresTheFilterStillCannotLeakOtherChunks() {
        // 存储层漏过滤（返回全目录）：HybridRecall 二次校验，仍只留满足过滤的分片。
        List<KnowledgeChunk> out = recall(true, false).search(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "LAB"));
        assertEquals(List.of("c-k-1", "c-k-2"), out.stream().map(KnowledgeChunk::chunkId).toList());
        assertFalse(out.contains(GLU));
        assertFalse(out.contains(IMG));

        RecallResult none = recall(true, false).searchWithReport(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("NOPE", null));
        assertTrue(none.chunks().isEmpty());
        assertEquals(RecallEmptyReason.SCALAR_EMPTY, none.emptyReason());
    }

    @Test
    void storeFailureWithFilterReturnsEmptyWithReasonNotFullCatalog() {
        RecallResult r = recall(false, true).searchWithReport(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "LAB"));
        assertTrue(r.chunks().isEmpty());
        assertEquals(RecallEmptyReason.STORE_UNAVAILABLE, r.emptyReason());
    }

    @Test
    void storeFailureWithoutFilterAlsoReturnsEmpty() {
        RecallResult r = recall(false, true).searchWithReport(RecallQuery.byFilterOnly(), RecallFilter.NONE);
        assertTrue(r.chunks().isEmpty());
        assertEquals(RecallEmptyReason.STORE_UNAVAILABLE, r.emptyReason());
    }

    @Test
    void fuzzyTextWithNoHitUnderFilterIsEmptyNotTheFilteredCandidates() {
        RecallResult r = recall(false, false).searchWithReport(
                new RecallQuery("完全无关的词汇zzz", 5), RecallFilter.ofIndicator("K", "LAB"));
        assertTrue(r.chunks().isEmpty());
        assertEquals(RecallEmptyReason.KEYWORD_EMPTY, r.emptyReason());
    }

    @Test
    void fuzzyTextOnlyRanksInsideTheFilteredCandidates() {
        RecallResult r = recall(false, false).searchWithReport(
                new RecallQuery("复查", 5), RecallFilter.ofIndicator("K", "LAB"));
        assertEquals(List.of("c-k-2"), r.chunks().stream().map(KnowledgeChunk::chunkId).toList());
        // 「血糖」只出现在 GLU 分片里：过滤到 K 时不能因为模糊词把 GLU 拉回来。
        RecallResult leak = recall(false, false).searchWithReport(
                new RecallQuery("血糖", 5), RecallFilter.ofIndicator("K", "LAB"));
        assertTrue(leak.chunks().isEmpty());
    }

    @Test
    void intersectWithEmptyIdsIsEmptyNeverFullCatalog() {
        assertTrue(HybridRecall.intersect(CATALOG, Set.of()).isEmpty());
        assertTrue(HybridRecall.intersect(CATALOG, null).isEmpty());
        assertTrue(HybridRecall.intersect(List.of(), Set.of("c-k-1")).isEmpty());
        assertTrue(HybridRecall.intersect(CATALOG, Set.of("not-in-catalog")).isEmpty());
        assertEquals(List.of(K1), HybridRecall.intersect(CATALOG, Set.of("c-k-1", "ghost")));
    }

    @Test
    void noFilterAndNoTextIsTheOnlyPathToTheWholePublishedCatalog() {
        RecallResult r = recall(false, false).searchWithReport(new RecallQuery(null, 20), RecallFilter.NONE);
        assertEquals(4, r.chunks().size());
        assertEquals(RecallEmptyReason.NONE, r.emptyReason());
    }

    @Test
    void limitIsClamped() {
        assertEquals(RecallQuery.DEFAULT_LIMIT, new RecallQuery(null, 0).effectiveLimit());
        assertEquals(RecallQuery.MAX_LIMIT, new RecallQuery(null, 999).effectiveLimit());
        assertEquals(1, recall(false, false).search(new RecallQuery(null, 1), RecallFilter.NONE).size());
    }

    // ---- 真库（H2）：只有 PUBLISHED 且在生效期内可被检索 ----

    @Test
    void publishedOnlyAndFilterEnforcedAgainstTheDatabase() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());

        List<String> k = springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "LAB"))
                .stream().map(KnowledgeChunk::chunkId).toList();
        assertEquals(List.of("chunk-k-1", "chunk-k-2"), k);
        assertFalse(k.contains("chunk-k-draft-1"), "DRAFT 文档不可被检索");

        assertTrue(springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("ALT", "LAB")).isEmpty());
        assertTrue(springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "IMAGING")).isEmpty());
        RecallResult miss = springRecall.searchWithReport(
                RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "IMAGING"));
        assertEquals(RecallEmptyReason.SCALAR_EMPTY, miss.emptyReason());
    }

    @Test
    void retiredAndNotYetEffectiveDocumentsAreNotSearchable() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        jdbc.update("UPDATE report_kb_document SET status = 'RETIRED' WHERE doc_id = 'doc-glu-guide'");
        jdbc.update("UPDATE report_kb_document SET effective_from = TIMESTAMP '2999-01-01 00:00:00' "
                + "WHERE doc_id = 'doc-k-guide'");
        jdbc.update("UPDATE report_kb_document SET status = 'PUBLISHED' WHERE doc_id = 'doc-k-draft'");
        jdbc.update("UPDATE report_kb_document SET effective_to = TIMESTAMP '2000-01-01 00:00:00' "
                + "WHERE doc_id = 'doc-k-draft'");

        assertTrue(springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("GLU", "LAB")).isEmpty());
        assertTrue(springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.ofIndicator("K", "LAB")).isEmpty());
        assertTrue(springRecall.search(RecallQuery.byFilterOnly(), RecallFilter.NONE).isEmpty());
    }

    @Test
    void indicatorWithoutKbDoesNotBorrowEvidenceFromOtherIndicatorsEndToEnd() throws Exception {
        InterpretationTestSupport.clearKb(jdbc);
        assertEquals(200, InterpretationTestSupport.reseed(mvc).path("code").asInt());
        String account = ReportTestSupport.uniq("acc-failclosed");
        // 知识库里有 K / GLU，但没有 ALT：ALT 报告不得借用别的指标依据，只能降级。
        String reportId = InterpretationTestSupport.createReport(mvc, account, "rag-alt-high.lab.txt");

        JsonNode res = InterpretationTestSupport.trigger(mvc, account, reportId);

        assertEquals(200, res.path("code").asInt());
        assertEquals("DEGRADED", res.path("data").path("status").asText());
        assertEquals(0, res.path("data").path("evidence").size());
        assertFalse(InterpretationTestSupport.claimTypes(res).contains("GUIDELINE"));
    }
}
