package gitgalaxy.backend.service;

import gitgalaxy.backend.entity.RepoAiSummary;
import gitgalaxy.backend.repository.RepoAiSummaryRepository;
import gitgalaxy.backend.repository.RepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiSummerize {

    private final LlmService llmService;
    private final RepoAiSummaryRepository repoAiSummaryRepository;
    private final RepoRepository repoRepository;
    private final GithubClient githubClient;

    public String summarize(String owner, String repo) {
        String readme = fetchReadme(owner, repo);
        if (readme == null || readme.isBlank()) {
            log.warn("[{}/{}] README 없음 — 요약 생략", owner, repo);
            return null;
        }

        String prompt =
                "You are summarizing a GitHub repository for a short 'About' section.\n\n" +
                "Summarize the project in exactly 2 sentences.\n\n" +
                "Rules:\n" +
                "- Sentence 1: Clearly state what the project is and what it does.\n" +
                "- Sentence 2: Describe its key concepts, architecture, or defining features.\n" +
                "- Focus on the overall purpose of the repository.\n" +
                "- Do NOT guess or use vague phrases like 'likely' or 'appears to'.\n" +
                "- Base your answer primarily on README content if available.\n" +
                "- Keep it concise, clear, and readable for developers.\n\n" +
                readme.substring(0, Math.min(readme.length(), 8000));

        try {
            String text = llmService.chat(prompt);
            if (text != null && !text.isBlank()) {
                upsertSummary(owner, repo, text.strip());
                log.info("[{}/{}] AI 요약 완료", owner, repo);
                return text.strip();
            }
            log.warn("[{}/{}] AI 요약 응답 비어있음", owner, repo);
            return null;
        } catch (Exception e) {
            log.error("[{}/{}] AI 요약 실패: {}", owner, repo, e.getMessage());
            throw new RuntimeException("AI 요약 실패: " + e.getMessage(), e);
        }
    }

    private String fetchReadme(String owner, String repo) {
        String branch = repoRepository.findByFullName(owner + "/" + repo)
                .map(r -> r.getDefaultBranch() != null ? r.getDefaultBranch() : "main")
                .orElse("main");

        for (String b : new String[]{branch, "master", "main"}) {
            try {
                return githubClient.getRawContent(owner, repo, b, "README.md");
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void upsertSummary(String owner, String repo, String summary) {
        RepoAiSummary entity = repoAiSummaryRepository
                .findByOwnerAndRepo(owner, repo)
                .orElseGet(RepoAiSummary::new);
        entity.setOwner(owner);
        entity.setRepo(repo);
        entity.setSummary(summary);
        repoAiSummaryRepository.save(entity);
    }
}
