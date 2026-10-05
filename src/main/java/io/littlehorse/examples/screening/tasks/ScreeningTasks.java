package io.littlehorse.examples.screening.tasks;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.REMOTE_ONLY;
import static io.littlehorse.examples.screening.policy.ScreeningPolicy.SENIORITY_SLACK;

import io.littlehorse.examples.screening.infra.AtsClient;
import io.littlehorse.examples.screening.infra.RecruitingToolClient;
import io.littlehorse.examples.screening.infra.VerificationClient;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Deterministic steps of candidate screening: outside-service calls, scoring math, and actions. */
@LHTask
public class ScreeningTasks {

    public static final String FETCH_APPLICATION = "fetch-application";
    public static final String FETCH_ROLE = "fetch-role";
    public static final String COMPUTE_FIT_SCORE = "compute-fit-score";
    public static final String FETCH_EMPLOYMENT_VERIFICATION = "fetch-employment-verification";
    public static final String SCHEDULE_INTERVIEW = "schedule-interview";
    public static final String CLOSE_APPLICATION = "close-application";
    public static final String REQUEST_RECRUITER_REVIEW = "request-recruiter-review";

    private static final Logger LOG = Logger.getLogger(ScreeningTasks.class);

    private final AtsClient ats;
    private final VerificationClient verification;
    private final RecruitingToolClient recruiting;
    private final LittleHorseBlockingStub lh;

    public ScreeningTasks(
            @RestClient AtsClient ats,
            @RestClient VerificationClient verification,
            @RestClient RecruitingToolClient recruiting,
            LittleHorseBlockingStub lh) {
        this.ats = ats;
        this.verification = verification;
        this.recruiting = recruiting;
        this.lh = lh;
    }

    /** Triggered by the recruiter-review user task itself; hands that UserTaskRun to the recruiting tool. */
    @LHTaskMethod(REQUEST_RECRUITER_REVIEW)
    public Map<String, Object> requestRecruiterReview(WorkerContext ctx) {
        UserTaskRunId task = lh.getNodeRun(ctx.getNodeRunId()).getUserTask().getUserTaskRunId();
        return recruiting.requestReview(new RecruitingToolClient.ReviewRequest(
                LHLibUtil.wfRunIdToString(task.getWfRunId()), task.getUserTaskGuid()));
    }

    /** Blind screening: the models never see the candidate's name or contact details. */
    @LHTaskMethod(FETCH_APPLICATION)
    public Map<String, Object> fetchApplication(String applicationId) {
        Map<String, Object> app = ats.application(applicationId);
        return Map.of(
                "application_id", app.get("application_id"),
                "resume", app.get("resume"),
                "cover_letter", app.get("cover_letter"));
    }

    @LHTaskMethod(FETCH_ROLE)
    public Map<String, Object> fetchRole(String track) {
        return ats.role(track);
    }

    /** Composite scoring: normalize each 0-4 Score to 0-1 and weight it by what this role cares about. */
    @LHTaskMethod(COMPUTE_FIT_SCORE)
    public Map<String, Object> computeFitScore(Map<String, Object> triage, Map<String, Object> role) {
        Map<?, ?> scores = (Map<?, ?>) triage.get("scores");
        Map<?, ?> weights = (Map<?, ?>) role.get("weights");
        Map<String, Object> breakdown = new LinkedHashMap<>();
        double composite = 0;
        for (Map.Entry<?, ?> w : weights.entrySet()) {
            double contribution = num(w.getValue()) * num(scores.get(w.getKey())) / 4;
            breakdown.put((String) w.getKey(), round(contribution));
            composite += contribution;
        }
        boolean seniorityOk = num(triage.get("seniority")) >= num(role.get("min_seniority")) - SENIORITY_SLACK;
        boolean remoteConflict = Boolean.TRUE.equals(role.get("onsite"))
                && num(triage.get("remote_only")) > REMOTE_ONLY;
        return Map.of(
                "composite", round(composite),
                "breakdown", breakdown,
                "seniority_ok", seniorityOk,
                "remote_conflict", remoteConflict);
    }

    @LHTaskMethod(FETCH_EMPLOYMENT_VERIFICATION)
    public Map<String, Object> fetchEmploymentVerification(String applicationId) {
        return verification.employment(applicationId);
    }

    @LHTaskMethod(SCHEDULE_INTERVIEW)
    public String scheduleInterview(String applicationId, String kind) {
        String interviewId = "INT-" + applicationId.substring(applicationId.indexOf('-') + 1) + "-" + kind;
        LOG.infof("Scheduled %s for %s (%s); invite sent to the candidate", kind, applicationId, interviewId);
        return interviewId;
    }

    @LHTaskMethod(CLOSE_APPLICATION)
    public void closeApplication(String applicationId, String reason) {
        LOG.infof("Closed %s: %s", applicationId, reason);
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    private static double round(double d) {
        return Math.round(d * 100) / 100.0;
    }
}
