package io.littlehorse.shared.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Minimal OpenAI Chat Completions types. */
public final class ChatCompletion {

    private ChatCompletion() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            String model,
            @JsonProperty("reasoning_effort") String reasoningEffort,
            List<Message> messages,
            @JsonProperty("response_format") Map<String, Object> responseFormat) {}

    public record Message(String role, String content) {}

    public record Response(String model, List<Choice> choices) {}

    public record Choice(Message message) {}
}
