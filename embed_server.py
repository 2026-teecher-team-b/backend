"""
BGE-M3 임베딩 서버 (FastAPI)
Spring Boot 백엔드에서 POST http://embed-server:8100/embed 로 호출
"""
from fastapi import FastAPI
from pydantic import BaseModel
from FlagEmbedding import BGEM3FlagModel

app = FastAPI()

print("BGE-M3 모델 로딩 중...")
_model = BGEM3FlagModel("BAAI/bge-m3", use_fp16=True)
print("모델 로딩 완료")


class EmbedRequest(BaseModel):
    text: str


@app.post("/embed")
def embed(req: EmbedRequest):
    text = req.text[:8000]
    output = _model.encode([text], max_length=512)
    return {"embedding": output["dense_vecs"][0].tolist()}


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8100)
