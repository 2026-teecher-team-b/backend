package gitgalaxy.backend.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {

    static final int DIMS = 1024;

    private static final String MODEL_NAME = "BAAI/bge-m3";
    // quantized (~400MB): 첫 실행 시 ~/.djl.ai 캐시에 자동 다운로드
    private static final String ONNX_URL =
            "https://huggingface.co/BAAI/bge-m3/resolve/main/onnx/model_quantized.onnx";

    private HuggingFaceTokenizer tokenizer;
    private ZooModel<NDList, NDList> model;
    private NDManager manager;

    @PostConstruct
    public void init() throws Exception {
        log.info("BGE-M3 로딩 중 (첫 실행 시 모델 다운로드 ~400MB)...");

        tokenizer = HuggingFaceTokenizer.newInstance(MODEL_NAME,
                Map.of("maxLength", "512", "padding", "true", "truncation", "true"));

        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optEngine("OnnxRuntime")
                .optModelUrls(ONNX_URL)
                .optTranslator(new NoopTranslator())
                .build();

        model = criteria.loadModel();
        manager = NDManager.newBaseManager();
        log.info("BGE-M3 로딩 완료 (dim={})", DIMS);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (manager != null) manager.close();
            if (model != null) model.close();
            if (tokenizer != null) tokenizer.close();
        } catch (Exception e) {
            log.warn("EmbeddingService 종료 오류: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return model != null && tokenizer != null;
    }

    public float[] embed(String text) {
        String input = text.length() > 8000 ? text.substring(0, 8000) : text;
        try {
            Encoding enc = tokenizer.encode(input);
            long seq = enc.getIds().length;

            try (NDManager sub = manager.newSubManager()) {
                NDArray inputIds = sub.create(enc.getIds(), new Shape(1, seq));
                NDArray attMask  = sub.create(enc.getAttentionMask(), new Shape(1, seq));
                inputIds.setName("input_ids");
                attMask.setName("attention_mask");

                NDList output;
                try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
                    output = predictor.predict(new NDList(inputIds, attMask));
                }

                // last_hidden_state [1, seq, 1024] → mean pool over non-padding tokens
                NDArray hidden = output.get(0);
                NDArray maskF  = attMask.toType(DataType.FLOAT32, false).reshape(1, seq, 1);
                NDArray summed = hidden.mul(maskF).sum(new int[]{1});
                NDArray count  = maskF.sum(new int[]{1}).clip(1e-9f, Float.MAX_VALUE);
                return summed.div(count).toFloatArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("BGE-M3 임베딩 실패: " + e.getMessage(), e);
        }
    }

    public static String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
