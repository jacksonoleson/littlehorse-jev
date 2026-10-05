package io.littlehorse.examples.screening.infra;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.RECRUITER_REVIEW_QUESTIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.ListVariablesRequest;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.Variable;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.shared.jev.JevModel;
import io.littlehorse.shared.models.ModelResponse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jboss.logging.Logger;

/**
 * Recruiting tool, served by this app, whose "recruiter" is Jev. It reads the WfRun's state, decides,
 * and completes the recruiter-review user task through the same API a recruiter's UI would use.
 */
@Path("/recruiting")
public class RecruitingToolResource {

    public static final String REVIEWER = "jev-recruiter";

    private static final Logger LOG = Logger.getLogger(RecruitingToolResource.class);

    private final LittleHorseBlockingStub lh;
    private final JevModel jev;
    private final ObjectMapper json;

    public RecruitingToolResource(LittleHorseBlockingStub lh, JevModel jev, ObjectMapper json) {
        this.lh = lh;
        this.jev = jev;
        this.json = json;
    }

    @POST
    @Path("/reviews")
    public Map<String, Object> requestReview(RecruitingToolClient.ReviewRequest request)
            throws JsonProcessingException {
        WfRunId wfRunId = LHLibUtil.wfRunIdFromString(request.wfRunId());
        UserTaskRunId taskId = UserTaskRunId.newBuilder()
                .setWfRunId(wfRunId)
                .setUserTaskGuid(request.userTaskGuid())
                .build();
        UserTaskRun task = lh.getUserTaskRun(taskId);

        // Sorted keys keep the request identical across restarts (see EXPERIMENTS.md #3).
        Map<String, Object> state = new TreeMap<>();
        for (Variable v : lh.listVariables(ListVariablesRequest.newBuilder().setWfRunId(wfRunId).build())
                .getResultsList()) {
            Object value = toJava(v.getValue());
            if (value != null) {
                state.put(v.getId().getName(), value);
            }
        }
        state.put("review_reason", task.getNotes());

        // Jev completes the userTask
        ModelResponse r = jev.ask(state, RECRUITER_REVIEW_QUESTIONS);
        ModelResponse.Answer decision = r.get("decision");
        String notes = "%s chose %s (confidence %.2f, probabilities %s)"
                .formatted(r.model(), decision.choice(), decision.confidence(), decision.probabilities());

        lh.completeUserTaskRun(CompleteUserTaskRunRequest.newBuilder()
                .setUserTaskRunId(taskId)
                .setUserId(REVIEWER)
                .putResults("decision", LHLibUtil.objToVarVal(decision.choice()))
                .putResults("notes", LHLibUtil.objToVarVal(notes))
                .build());
        LOG.infof("Recruiter review %s | reason: %s | %s", request.wfRunId(), task.getNotes(), notes);
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
