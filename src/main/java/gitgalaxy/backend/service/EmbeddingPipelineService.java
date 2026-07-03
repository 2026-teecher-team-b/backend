package gitgalaxy.backend.service;

import gitgalaxy.backend.model.ChunkDocument;
import gitgalaxy.backend.repository.ChunkEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingPipelineService {

    private static final int BATCH_SIZE = 20;

    /**
     * 동시에 진행할 배치(=Vertex AI 호출) 상한.
     * Virtual Thread는 배치 수만큼 만들 수 있지만, 외부 임베딩 API 레이트리밋과
     * DB 커넥션 풀(HikariCP) 고갈을 막기 위해 실제 동시 실행은 이 값으로 제한한다.
     */
    private static final int MAX_CONCURRENCY = 8;

    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;

    public void embedAndStore(List<ChunkDocument> chunks) {
        if (!embeddingService.isConfigured()) {
            log.warn("GEMINI_API_KEY 미설정 → 임베딩 스킵 ({} 청크)", chunks.size());
            return;
        }

        // 멱등: 이미 임베딩된 청크는 건너뛰고, 실패분만 재실행 시 이어서 처리
        List<ChunkDocument> toEmbed = chunks.stream()
                .filter(chunk -> !chunkEmbeddingRepository.existsByChunkId(chunk.getChunkId()))
                .toList();
        int skipped = chunks.size() - toEmbed.size();

        // 배치 분할
        List<List<ChunkDocument>> batches = new ArrayList<>();
        for (int i = 0; i < toEmbed.size(); i += BATCH_SIZE) {
            batches.add(toEmbed.subList(i, Math.min(i + BATCH_SIZE, toEmbed.size())));
        }
        int totalBatches = batches.size();
        if (totalBatches == 0) {
            log.info("임베딩 완료: 저장=0 / 스킵(기존)={} / 전체={}", skipped, chunks.size());
            return;
        }
        log.info("배치 임베딩 시작(Virtual Thread 병렬, 동시 상한 {}): 총 {}개 청크 → {}번 API 호출 예정",
                MAX_CONCURRENCY, toEmbed.size(), totalBatches);

        AtomicInteger stored = new AtomicInteger();
        AtomicInteger failedBatches = new AtomicInteger();
        Semaphore limiter = new Semaphore(MAX_CONCURRENCY);

        // 배치별로 경량 Virtual Thread를 할당해 I/O 대기(임베딩 API 호출)를 병렬로 겹친다.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(totalBatches);
            for (int b = 0; b < totalBatches; b++) {
                final int batchNum = b + 1;
                final List<ChunkDocument> batch = batches.get(b);
                futures.add(executor.submit(() -> {
                    limiter.acquireUninterruptibly();
                    try {
                        List<String> texts = batch.stream().map(ChunkDocument::getContent).toList();
                        List<float[]> vectors = embeddingService.embedBatch(texts);
                        for (int j = 0; j < batch.size(); j++) {
                            chunkEmbeddingRepository.upsert(
                                    batch.get(j), EmbeddingService.toVectorString(vectors.get(j)));
                            stored.incrementAndGet();
                        }
                        log.info("배치 {}/{} 완료 ({}개)", batchNum, totalBatches, batch.size());
                    } catch (Exception e) {
                        // 배치 단위 실패 격리: 한 배치가 실패해도 나머지는 계속 진행
                        failedBatches.incrementAndGet();
                        log.warn("배치 {}/{} 실패 (스킵): {}", batchNum, totalBatches, e.getMessage());
                    } finally {
                        limiter.release();
                    }
                }));
            }
            // 모든 배치 완료 대기
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("배치 작업 대기 중 오류: {}", e.getMessage());
                }
            }
        }

        log.info("임베딩 완료: 저장={} / 스킵(기존)={} / 실패배치={} / 전체={} / API 호출={}",
                stored.get(), skipped, failedBatches.get(), chunks.size(), totalBatches);
    }
}
