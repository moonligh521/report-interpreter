package com.moonligh.report.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moonligh.report.domain.kb.KbChunkStore;
import com.moonligh.report.domain.kb.KbDocumentStore;
import com.moonligh.report.domain.kb.KnowledgeChunk;

@Component
public class KbStoreImpl implements KbChunkStore, KbDocumentStore {

    private final ReportKbDocumentMapper documents;
    private final ReportKbChunkMapper chunks;

    public KbStoreImpl(ReportKbDocumentMapper documents, ReportKbChunkMapper chunks) {
        this.documents = documents;
        this.chunks = chunks;
    }

    @Override
    public List<KnowledgeChunk> listPublished(Instant now, String indicatorCode, String specialty) {
        return chunks.listPublishedChunks(now, indicatorCode, specialty).stream()
                .map(r -> new KnowledgeChunk(
                        r.chunkId(), r.docId(), r.title(), r.content(), r.indicatorCode(),
                        r.specialty(), r.url(), r.kbVersion(), r.authorityLevel()))
                .toList();
    }

    @Override
    public Optional<String> currentKbVersion(Instant now) {
        return Optional.ofNullable(documents.maxPublishedKbVersion(now));
    }

    @Override
    public void upsertDocument(KbDocumentRow document, List<KbChunkRow> rows) {
        if (documents.update(document) == 0) {
            documents.insert(document);
        }
        chunks.deleteByDoc(document.docId());
        for (KbChunkRow row : rows) {
            chunks.insert(row);
        }
    }
}
