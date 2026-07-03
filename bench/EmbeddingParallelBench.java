import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GitGalaxy 배치 임베딩: 순차 vs Virtual Thread 병렬 wall-clock 벤치마크.
 *
 * 실제 embedBatch(Vertex AI 호출)는 네트워크 I/O 대기가 지배적이므로,
 * 그 지연을 LATENCY_MS 고정 sleep으로 모사해 파이프라인 구조만 비교한다.
 * (프로덕션 수치는 Vertex 키로 real 파이프라인을 돌려 교체 가능)
 *
 * 실행: java EmbeddingParallelBench.java
 */
public class EmbeddingParallelBench {

    static final int BATCH_SIZE = 20;
    static final int LATENCY_MS = 400;      // 임베딩 API 배치 호출 1회당 지연(가정)
    static final int MAX_CONCURRENCY = 8;   // 운영 코드와 동일한 동시성 상한

    // 임베딩 API 호출 1회를 모사 (I/O 대기)
    static void embedBatchStub() {
        try { Thread.sleep(LATENCY_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // 기존: 순차 배치 처리
    static long runSequential(int totalBatches) {
        long t0 = System.nanoTime();
        for (int b = 0; b < totalBatches; b++) {
            embedBatchStub();
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    // 개선: Virtual Thread 병렬 + 동시성 상한(Semaphore)
    static long runParallel(int totalBatches) {
        long t0 = System.nanoTime();
        AtomicInteger done = new AtomicInteger();
        Semaphore limiter = new Semaphore(MAX_CONCURRENCY);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(totalBatches);
            for (int b = 0; b < totalBatches; b++) {
                futures.add(executor.submit(() -> {
                    limiter.acquireUninterruptibly();
                    try { embedBatchStub(); done.incrementAndGet(); }
                    finally { limiter.release(); }
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) {
        int[] chunkCounts = {200, 400, 800};
        System.out.printf("배치당 API 지연=%dms, 배치 크기=%d, 동시성 상한=%d%n%n",
                LATENCY_MS, BATCH_SIZE, MAX_CONCURRENCY);
        System.out.printf("%-8s %-8s %-12s %-12s %-10s%n",
                "청크", "배치수", "순차(ms)", "병렬(ms)", "단축률");
        for (int chunks : chunkCounts) {
            int totalBatches = (int) Math.ceil((double) chunks / BATCH_SIZE);
            long seq = runSequential(totalBatches);
            long par = runParallel(totalBatches);
            double reduction = (1.0 - (double) par / seq) * 100.0;
            System.out.printf("%-8d %-8d %-12d %-12d %.1f%%%n",
                    chunks, totalBatches, seq, par, reduction);
        }
    }
}
