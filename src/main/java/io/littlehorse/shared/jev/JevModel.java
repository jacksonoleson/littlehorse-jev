package io.littlehorse.shared.jev;

import io.littlehorse.shared.models.ModelResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class JevModel {

    private final TypeSafeClient client;
    private final String model;

    public JevModel(@RestClient TypeSafeClient client, @ConfigProperty(name = "typesafe.model") String model) {
        this.client = client;
        this.model = model;
    }

    public ModelResponse ask(Object state, Map<String, SystemOne.Question> questions) {
        SystemOne.Response r = client.systemOne(new SystemOne.Request(state, model, questions));
        Map<String, ModelResponse.Answer> answers = new LinkedHashMap<>();
        r.answers().forEach((id, a) -> answers.put(id, new ModelResponse.Answer(
                a.choice(), a.noul(), a.score(), a.confidence(), a.probabilities())));
        return new ModelResponse(r.model(), answers);
    }
}
