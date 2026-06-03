package gitgalaxy.backend.service;

import gitgalaxy.backend.repository.ChunkEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private static final int TOP_K = 5;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final LlmService llmService;
    private final StringRedisTemplate redisTemplate;

    /** 특정 repo에 대한 질문 답변 (Cache-Aside) */
    public String explain(String owner, String repo, String question) {
        String cacheKey = "rag:" + owner + ":" + repo + ":" + hashQuestion(question);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("RAG cache hit: {}", cacheKey);
            return cached;
        }

        String queryVector = toQueryVector(question);
        List<Map<String, Object>> chunks = chunkEmbeddingRepository.findSimilar(queryVector, owner, repo, TOP_K);

        if (chunks.isEmpty()) {
            return "해당 repo(" + owner + "/" + repo + ")의 문서 데이터가 없습니다. "
                    + "먼저 POST /admin/collect 로 수집을 실행하세요.";
        }

        log.debug("RAG: {}/{} 질문='{}' → {}개 청크 검색됨", owner, repo, question, chunks.size());
        String answer = llmService.chat(buildPrompt(owner + "/" + repo, question, buildContext(chunks)));

        redisTemplate.opsForValue().set(cacheKey, answer, CACHE_TTL);
        return answer;
    }

    /** 전체 repo 대상 글로벌 검색 (Cache-Aside) */
    public String explainGlobal(String question) {
        String cacheKey = "rag:global:" + hashQuestion(question);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("RAG global cache hit: {}", cacheKey);
            return cached;
        }

        String queryVector = toQueryVector(question);
        List<Map<String, Object>> chunks = chunkEmbeddingRepository.findSimilarGlobal(queryVector, TOP_K);

        if (chunks.isEmpty()) {
            return "수집된 문서 데이터가 없습니다. POST /admin/collect 로 수집을 먼저 실행하세요.";
        }

        log.debug("RAG global: 질문='{}' → {}개 청크 검색됨", question, chunks.size());
        String answer = llmService.chat(buildPrompt("GitHub repos", question, buildContext(chunks)));

        redisTemplate.opsForValue().set(cacheKey, answer, CACHE_TTL);
        return answer;
    }

    /** 레포 데이터 업데이트 시 해당 레포 캐시 무효화 */
    public void evictRepoCache(String owner, String repo) {
        String pattern = "rag:" + owner + ":" + repo + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("RAG cache evicted: {} keys for {}/{}", keys.size(), owner, repo);
        }
    }

    // ────────────────────────────────────────────────

    private String toQueryVector(String question) {
        float[] embedding = embeddingService.embed(question);
        return EmbeddingService.toVectorString(embedding);
    }

    private String buildContext(List<Map<String, Object>> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> c = chunks.get(i);
            sb.append("=== 문서 ").append(i + 1).append(" ===\n")
              .append("출처: ").append(c.get("repo_owner")).append("/").append(c.get("repo_name")).append('\n')
              .append("파일: ").append(c.get("file_path")).append('\n')
              .append("섹션: ").append(c.get("heading")).append('\n')
              .append(c.get("content")).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPrompt(String target, String question, String context) {
        return """
                당신은 GitHub 레포지토리 분석 전문가입니다.
                아래는 '%s' 레포지토리의 실제 문서 내용입니다.

                [참고 문서]
                %s

                위 문서를 바탕으로 다음 질문에 답하세요:
                %s

                - 반드시 위 문서 내용을 근거로 답하세요.
                - 문서에 없는 내용은 "문서에 명시되지 않았습니다"라고 하세요.
                - 한국어로 답변하세요.
                """.formatted(target, context, question);
    }

    private String hashQuestion(String question) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(question.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(question.hashCode());
        }
    }
}
