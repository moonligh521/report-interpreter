package com.moonligh.report.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.report.api.req.KbReseedReq;
import com.moonligh.report.api.res.KbReseedRes;
import com.moonligh.report.domain.interpret.InterpretationVersions;
import com.moonligh.report.domain.kb.KbDocumentStore;
import com.moonligh.report.domain.kb.KbFixtureDocument;
import com.moonligh.report.infrastructure.persistence.KbChunkRow;
import com.moonligh.report.infrastructure.persistence.KbDocumentRow;

/**
 * 内部 KB 重建：把 classpath:kb/** 夹具幂等 upsert 进 report_kb_document / report_kb_chunk。
 * 只允许 classpath 来源（禁止 file: / http: / 路径穿越）；dryRun 只校验与计数，不写库。
 */
@Service
public class KbAdminService {

    public static final String DEFAULT_SOURCE = "classpath:kb/lab-min";

    private static final Pattern SAFE_SOURCE = Pattern.compile("classpath:kb(/[A-Za-z0-9_\\-]+)+");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "RETIRED");
    private static final int MAX_CONTENT = 2000;

    private final KbDocumentStore store;
    private final ObjectMapper json = new ObjectMapper();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public KbAdminService(KbDocumentStore store) {
        this.store = store;
    }

    @Transactional
    public KbReseedRes reseed(KbReseedReq req) {
        String source = req == null || !StringUtils.hasText(req.source()) ? DEFAULT_SOURCE : req.source().strip();
        boolean dryRun = req != null && req.dryRunEnabled();
        if (!SAFE_SOURCE.matcher(source).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "source 仅支持 classpath:kb/ 下的目录");
        }
        List<KbFixtureDocument> docs = load(source);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "source 下没有可加载的 KB 文档");
        }
        int chunkCount = 0;
        List<KbDocumentRow> docRows = new ArrayList<>();
        List<List<KbChunkRow>> chunkRows = new ArrayList<>();
        for (KbFixtureDocument d : docs) {
            KbDocumentRow doc = toDocument(d);
            List<KbChunkRow> chunks = toChunks(d);
            docRows.add(doc);
            chunkRows.add(chunks);
            chunkCount += chunks.size();
        }
        if (!dryRun) {
            for (int i = 0; i < docRows.size(); i++) {
                store.upsertDocument(docRows.get(i), chunkRows.get(i));
            }
        }
        String kbVersion = docRows.stream()
                .filter(d -> "PUBLISHED".equals(d.status()))
                .map(KbDocumentRow::kbVersion)
                .max(Comparator.naturalOrder())
                .orElse(InterpretationVersions.KB_EMPTY);
        return new KbReseedRes(docRows.size(), chunkCount, kbVersion);
    }

    // ---- 内部 ----

    private List<KbFixtureDocument> load(String source) {
        Resource[] resources;
        try {
            resources = resolver.getResources(source + "/*.json");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无法读取 KB 夹具目录");
        }
        Arrays.sort(resources, Comparator.comparing(r -> String.valueOf(r.getFilename())));
        List<KbFixtureDocument> out = new ArrayList<>();
        for (Resource r : resources) {
            try (InputStream in = r.getInputStream()) {
                out.add(json.readValue(in, KbFixtureDocument.class));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 夹具格式不合法：" + r.getFilename());
            }
        }
        return out;
    }

    private static KbDocumentRow toDocument(KbFixtureDocument d) {
        require(d.docId(), "docId");
        require(d.title(), "title");
        require(d.kbVersion(), "kbVersion");
        require(d.authorityLevel(), "authorityLevel");
        if (d.status() == null || !STATUSES.contains(d.status())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 文档 status 不合法：" + d.docId());
        }
        Instant from = instant(d.effectiveFrom(), d.docId());
        if (from == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 文档缺少 effectiveFrom：" + d.docId());
        }
        return new KbDocumentRow(
                d.docId(), d.title(), d.kbVersion(), d.status(), d.authorityLevel(), d.url(),
                from, instant(d.effectiveTo(), d.docId()), null, null);
    }

    private static List<KbChunkRow> toChunks(KbFixtureDocument d) {
        if (d.chunks() == null || d.chunks().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 文档没有分片：" + d.docId());
        }
        List<KbChunkRow> rows = new ArrayList<>();
        for (KbFixtureDocument.KbFixtureChunk c : d.chunks()) {
            require(c.chunkId(), "chunkId");
            require(c.content(), "content");
            if (c.content().length() > MAX_CONTENT) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 分片过长：" + c.chunkId());
            }
            rows.add(new KbChunkRow(
                    c.chunkId(), d.docId(), c.content(), d.title(), c.indicatorCode(), c.specialty(),
                    d.url(), null, null));
        }
        return rows;
    }

    private static Instant instant(String text, String docId) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Instant.parse(text.strip());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 文档时间格式不合法：" + docId);
        }
    }

    private static void require(String v, String field) {
        if (!StringUtils.hasText(v)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "KB 夹具缺少字段 " + field);
        }
    }
}
