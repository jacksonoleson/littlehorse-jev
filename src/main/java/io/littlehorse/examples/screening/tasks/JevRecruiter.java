package io.littlehorse.examples.screening.tasks;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.RECRUITER_REVIEW_QUESTIONS;
import static io.littlehorse.shared.models.DecisionModels.JEV;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.ListVariablesRequest;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.Variable;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.shared.models.DecisionModels;
import io.littlehorse.shared.models.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Jev as the recruiter. Triggered by the recruiter-review user task itself: reads the WfRun's state, decides,
 * and completes the user task through the same API a recruiter's UI would use.
 */
@LHTask
public class JevRecruiter {

    public static final String COMPLETE_RECRUITER_REVIEW = "complete-recruiter-review";
    public static final String REVIEWER = "jev-recruiter";

    private final DecisionModels models;
    private final LittleHorseBlockingStub lh;
    private final ObjectMapper json;

    public JevRecruiter(DecisionModels models, LittleHorseBlockingStub lh, ObjectMapper json) {
        this.models = models;
        this.lh = lh;
        this.json = json;
    }

    @LHTaskMethod(COMPLETE_RECRUITER_REVIEW)
    public Map<String, Object> completeRecruiterReview(WorkerContext ctx) throws Exception {
        // A reminder task's node run is the user task node that scheduled it.
        UserTaskRunId taskId = lh.getNodeRun(ctx.getNodeRunId()).getUserTask().getUserTaskRunId();
        UserTaskRun task = lh.getUserTaskRun(taskId);

        Map<String, Object> state = new TreeMap<>();
        for (Variable v : lh.listVariables(ListVariablesRequest.newBuilder().setWfRunId(taskId.getWfRunId()).build())
                .getResultsList()) {
            Object value = toJava(v.getValue());
            if (value != null) {
                state.put(v.getId().getName(), value);
            }
        }
        state.put("review_reason", task.getNotes());

        ModelResponse r = models.ask(JEV, ctx, state, RECRUITER_REVIEW_QUESTIONS);
        ModelResponse.Answer decision = r.get("decision");
        String notes = "%s chose %s (confidence %.2f, probabilities %s)"
                .formatted(r.model(), decision.choice(), decision.confidence(), decision.probabilities());

        lh.completeUserTaskRun(CompleteUserTaskRunRequest.newBuilder()
                .setUserTaskRunId(taskId)
                .setUserId(REVIEWER)
                .putResults("decision", LHLibUtil.objToVarVal(decision.choice()))
                .putResults("notes", LHLibUtil.objToVarVal(notes))
                .build());
        return Map.of("reviewer", REVIEWER, "decision", decision.choice(), "confidence", decision.confidence());
    }

    private Object toJava(VariableValue v) throws JsonProcessingException {
        return switch (v.getValueCase()) {
            case STR -> v.getStr();
            case JSON_OBJ -> json.readValue(v.getJsonObj(), Map.class);
            case JSON_ARR -> json.readValue(v.getJsonArr(), List.class);
            case DOUBLE -> v.getDouble();
            case INT -> v.getInt();
            case BOOL -> v.getBool();
            default -> null;
        };
    }
}
