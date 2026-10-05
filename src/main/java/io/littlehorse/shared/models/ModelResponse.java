package io.littlehorse.shared.models;

import java.util.LinkedHashMap;
import java.util.Map;

/** Engine-neutral answers to a set of typed questions. Fields an engine doesn't produce are null. */
public record ModelResponse(String model, Map<String, Answer> answers) {

    public record Answer(
            String choice, Double noul, Double score, Double confidence, Map<String, Double> probabilities) {}

    public Answer get(String questionId) {
        return answers.get(questionId);
    }

    /** Flat JSON result for the WfSpec: the given key/value pairs (nulls dropped) plus the model name. */
    public Map<String, Object> toResult(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                out.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        out.put("model", model);
        return out;
    }
}
