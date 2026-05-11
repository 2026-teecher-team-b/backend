# GitGalaxy Backend API 명세서

> **Base URL**: `http://localhost:8080` (개발) / `https://api.gitgalaxy.io` (운영)  
> **Content-Type**: `application/json`  
> ✅ = 구현 완료 | 🔲 = 미구현 (예정)

---

## 1. Repo 목록 / 3D 렌더링 데이터

### ✅ `GET /repos`
전체 추적 repo 목록. 3D 은하계 별 렌더링에 사용.

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `lang` | string | 언어 필터 (예: `Java`, `Python`) |
| `topic` | string | 토픽 필터 (예: `ai`, `web`) |
| `period` | string | 기간 필터: `day` / `week` / `month` |
| `limit` | int | 최대 개수 (기본 500) |

**Response**
```json
[
  {
    "id": 1,
    "fullName": "langchain-ai/langchain",
    "owner": "langchain-ai",
    "name": "langchain",
    "description": "Building applications with LLMs",
    "starCount": 92000,
    "defaultBranch": "main",
    "tracked": true,
    "lastCollectedAt": "2025-04-29T10:00:00"
  }
]
```

---

### 🔲 `GET /repos/trending`
현재 트렌딩 repo 목록. 스코어 기준 상위 정렬.

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `period` | string | `day` / `week` / `month` (기본: `day`) |
| `limit` | int | 최대 개수 (기본 50) |

**Response**
```json
[
  {
    "rank": 1,
    "fullName": "langchain-ai/langchain",
    "activityScore": 92.4,
    "healthScore": 88.1,
    "scoreDelta": "+12.3",
    "starCount": 92000,
    "language": "Python",
    "topics": ["llm", "ai", "rag"]
  }
]
```

---

### 🔲 `GET /repos/clusters`
언어 / 토픽 기반 군집 데이터. 3D 성단(Cluster) 매핑에 사용.

**Response**
```json
[
  {
    "clusterId": "python",
    "label": "Python",
    "repos": [
      {
        "fullName": "langchain-ai/langchain",
        "activityScore": 92.4,
        "x": 12.3,
        "y": -4.1,
        "z": 7.8
      }
    ]
  }
]
```
> 좌표(x, y, z)는 백엔드에서 군집 알고리즘으로 계산하여 제공.  
> 프론트는 좌표 그대로 Three.js에 배치.

---

## 2. Repo 상세 (사이드 패널)

### ✅ `GET /repos/{owner}/{repo}`
repo 상세 정보. 별 클릭 시 우측 사이드 패널에 표시.

**Path Parameters**: `owner` (예: `langchain-ai`), `repo` (예: `langchain`)

**Response**
```json
{
  "id": 1,
  "fullName": "langchain-ai/langchain",
  "owner": "langchain-ai",
  "name": "langchain",
  "description": "Building applications with LLMs",
  "starCount": 92000,
  "defaultBranch": "main",
  "tracked": true,
  "lastCollectedAt": "2025-04-29T10:00:00",
  "createdAt": "2025-04-01T00:00:00"
}
```

> 미구현 필드 (추후 추가 예정): `activityScore`, `healthScore`, `language`, `topics`, `openIssues`, `forks`

---

### 🔲 `GET /repos/{owner}/{repo}/score`
스코어 히스토리. 사이드 패널 차트에 사용.

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `period` | string | `day` / `week` / `month` |

**Response**
```json
{
  "fullName": "langchain-ai/langchain",
  "period": "week",
  "history": [
    { "timestamp": "2025-04-23T00:00:00", "activityScore": 78.2, "healthScore": 85.0 },
    { "timestamp": "2025-04-24T00:00:00", "activityScore": 81.5, "healthScore": 85.3 },
    { "timestamp": "2025-04-29T00:00:00", "activityScore": 92.4, "healthScore": 88.1 }
  ]
}
```

---

### 🔲 `GET /repos/{owner}/{repo}/activity`
최근 활동 타임라인 (Commit / PR / Issue / Star).

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `period` | string | `day` / `week` / `month` |
| `type` | string | `commit` / `pr` / `issue` / `star` / 생략 시 전체 |

**Response**
```json
{
  "fullName": "langchain-ai/langchain",
  "events": [
    { "type": "commit", "count": 42, "timestamp": "2025-04-29T09:00:00" },
    { "type": "pr",     "count": 8,  "timestamp": "2025-04-29T09:00:00" },
    { "type": "issue",  "count": 15, "timestamp": "2025-04-29T09:00:00" },
    { "type": "star",   "count": 230,"timestamp": "2025-04-29T09:00:00" }
  ]
}
```

---

## 3. AI / RAG

### ✅ `GET /repos/{owner}/{repo}/explain`
RAG 기반 자유 질문. 문서 검색 후 LLM이 답변.

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `q` | string | 질문 (예: `이 레포에 vector store 기능 있어?`) |

**Response**
```json
{
  "repo": "langchain-ai/langchain",
  "question": "이 레포에 vector store 기능 있어?",
  "answer": "네, LangChain은 다양한 Vector Store를 지원합니다..."
}
```

---

### ✅ `GET /repos/search`
전체 repo 대상 글로벌 RAG 검색.

**Query Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `q` | string | 질문 (예: `RAG 구현에 쓸 수 있는 레포 알려줘`) |

**Response**
```json
{
  "question": "RAG 구현에 쓸 수 있는 레포 알려줘",
  "answer": "RAG 구현에 적합한 레포로는 langchain, openai-cookbook 등이 있습니다..."
}
```

---

### 🔲 `GET /repos/{owner}/{repo}/insight`
"왜 점수가 올랐어?" 자동 인사이트 생성. RAG 기반 3줄 요약.

**Response**
```json
{
  "repo": "langchain-ai/langchain",
  "generatedAt": "2025-04-29T10:00:00",
  "summary": "최근 3일간 PR 머지가 32건 급증했으며, 새로운 RAG 관련 문서가 추가되었습니다. Star 수 역시 하루 230개 증가하며 커뮤니티 관심이 높아졌습니다.",
  "scoreDelta": "+12.3",
  "keyEvents": [
    { "type": "pr_merge", "count": 32 },
    { "type": "star",     "count": 230 }
  ]
}
```

---

## 4. 즐겨찾기 (마이페이지)

### 🔲 `GET /favorites`
즐겨찾기 repo 목록.

**Response**
```json
[
  {
    "fullName": "langchain-ai/langchain",
    "starCount": 92000,
    "activityScore": 92.4
  }
]
```

### 🔲 `POST /favorites/{owner}/{repo}`
즐겨찾기 추가.

**Response** `200 OK`

### 🔲 `DELETE /favorites/{owner}/{repo}`
즐겨찾기 삭제.

**Response** `200 OK`

---

## 5. 실시간 (WebSocket)

### 🔲 WebSocket STOMP 연결

```
ws://localhost:8080/ws
```

| Topic | 방향 | 설명 |
|---|---|---|
| `/topic/scores` | BE → FE | 스코어 변화 전체 broadcast |
| `/topic/events/{owner}/{repo}` | BE → FE | 특정 repo 이벤트 발생 알림 |
| `/topic/trending` | BE → FE | 트렌딩 순위 변동 알림 |

**`/topic/scores` 메시지 예시**
```json
{
  "fullName": "langchain-ai/langchain",
  "activityScore": 92.4,
  "scoreDelta": "+3.2",
  "brightness": 2.1
}
```

**`/topic/events/{owner}/{repo}` 메시지 예시**
```json
{
  "type": "SPIKE",
  "message": "🔥 langchain 급상승 감지",
  "timestamp": "2025-04-29T10:05:00"
}
```

> SSE 대신 WebSocket(STOMP) 사용 예정.  
> FE는 Zustand로 상태 즉시 업데이트.

---

## 6. 관리 API (Admin)

### ✅ `POST /admin/collect`
repos.json 기준 전체 수집 수동 트리거.

**Response**
```json
{ "total": 4, "success": 4, "failed": 0, "skipped": 0 }
```

### ✅ `POST /admin/collect/{owner}/{repo}`
단일 repo 즉시 수집.

**Response**
```json
{
  "owner": "langchain-ai",
  "repo": "langchain",
  "status": "success",
  "fileCount": 42,
  "chunkCount": 318,
  "durationMs": 12400
}
```

---

## 구현 현황 요약

| 우선순위 | API | 상태 |
|---|---|---|
| P1 | `GET /repos` | ✅ 완료 |
| P1 | `GET /repos/{owner}/{repo}` | ✅ 완료 |
| P1 | `GET /repos/trending` | 🔲 미구현 |
| P1 | `GET /repos/clusters` | 🔲 미구현 |
| P1 | WebSocket STOMP `/ws` | 🔲 미구현 |
| P1 | `GET /repos/{owner}/{repo}/score` | 🔲 미구현 |
| P1 | `GET /repos/{owner}/{repo}/activity` | 🔲 미구현 |
| P2 | `GET /repos/search` | ✅ 완료 |
| P3 | `GET /repos/{owner}/{repo}/explain` | ✅ 완료 |
| P3 | `GET /repos/{owner}/{repo}/insight` | 🔲 미구현 |
| Low | `GET /favorites` | 🔲 미구현 |
| Low | `POST /favorites/{owner}/{repo}` | 🔲 미구현 |
| Low | `DELETE /favorites/{owner}/{repo}` | 🔲 미구현 |
| Admin | `POST /admin/collect` | ✅ 완료 |
| Admin | `POST /admin/collect/{owner}/{repo}` | ✅ 완료 |
