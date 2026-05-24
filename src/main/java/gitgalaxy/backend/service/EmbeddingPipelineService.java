package gitgalaxy.backend.service;

import gitgalaxy.backend.model.ChunkDocument;
import gitgalaxy.backend.repository.ChunkEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingPipelineService {

    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;

    public void embedAndStore(List<ChunkDocument> chunks) {
        if (!embeddingService.isConfigured()) {
            log.warn("Vertex AI 미초기화 → 임베딩 스킵 ({} 청크)", chunks.size());
            return;
        }

        int stored = 0;
        int skipped = 0;

        for (ChunkDocument chunk : chunks) {
            try {
                if (chunkEmbeddingRepository.existsByChunkId(chunk.getChunkId())) {
                    skipped++;
                    continue;
                }
                float[] vector = embeddingService.embed(chunk.getContent());
                chunkEmbeddingRepository.upsert(chunk, EmbeddingService.toVectorString(vector));
                stored++;
            } catch (Exception e) {
                log.warn("임베딩 실패 (청크 스킵): {} → {}", chunk.getChunkId(), e.getMessage());
            }
        }

        log.info("임베딩 완료: 저장={} / 스킵(기존)={} / 전체={}", stored, skipped, chunks.size());
    }
}
