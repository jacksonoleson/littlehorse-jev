package io.littlehorse.common.models;

import io.littlehorse.common.jev.JevModel;
import io.littlehorse.common.jev.SystemOne;
import io.littlehorse.common.llm.OpenAiModel;
import io.littlehorse.sdk.worker.WorkerContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.jboss.logging.Logger;

/** Sends typed questions to the chosen engine and logs every answer to the task run's audit log. */
@ApplicationScoped
public class DecisionModels {

    public static final String JEV = "jev";
    public static final String OPENAI = "openai";

    private static final Logger LOG = Logger.getLogger(DecisionModels.class);

    private final JevModel jev;
    private final OpenAiModel openAi;

    public DecisionModels(JevModel jev, OpenAiModel openAi) {
        this.jev = jev;
        this.openAi = openAi;
    }

    public ModelResponse ask(
            String engine, WorkerContext ctx, Object state, Map<String, SystemOne.Question> questions)
            throws Exception {
        ModelResponse r = switch (engine) {
            case JEV -> jev.ask(state, questions);
            case OPENAI -> openAi.ask(state, questions);
            default -> throw new IllegalArgumentException("Unknown engine " + engine);
        };
        String audit = "engine=%s model=%s answers=%s".formatted(engine, r.model(), r.answers());
        LOG.info(audit);
        ctx.log(audit);
        return r;
    }
}
