package io.littlehorse.common.jev;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Request/response types for the TypeSafe System One API (https://docs.typesafe.ai/api). */
public final class SystemOne {

    private SystemOne() {}

    public record Request(Object state, String model, Map<String, Question> questions) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(String type, Object instructions, Object criteria) {

        public static Question choice(Object instructions, Map<String, ?> criteria) {
            return new Question("choice", instructions, criteria);
        }

        public static Question noul(Object instructions) {
            return new Question("noul", instructions, null);
        }

        public static Question score(String instructions, List<String> levels) {
            return new Question("score", instructions, levels);
        }
    }

    public record Response(String model, Map<String, Answer> answers) {}

    public record Answer(
            String type,
            String choice,
            Double noul,
            Double score,
            Double confidence,
            Map<String, Double> probabilities) {}
}
