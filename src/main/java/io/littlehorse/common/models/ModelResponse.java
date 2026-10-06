package io.littlehorse.common.models;

import java.util.Map;

/** Engine-neutral answers to a set of typed questions. Fields an engine doesn't produce are null. */
public record ModelResponse(String model, Map<String, Answer> answers) {

    public record Answer(
            String choice, Double noul, Double score, Double confidence, Map<String, Double> probabilities) {}

    public Answer get(String questionId) {
        return answers.get(questionId);
    }
}
