package io.littlehorse.examples.screening.tasks;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.REMOTE_ONLY;
import static io.littlehorse.examples.screening.policy.ScreeningPolicy.SENIORITY_SLACK;

import io.littlehorse.examples.screening.infra.Ats;
import io.littlehorse.examples.screening.infra.EmploymentRecords;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/** Deterministic steps of candidate screening: ATS and verification lookups, scoring math, and actions. */
@LHTask
public class ScreeningTasks {

    public static final String FETCH_APPLICATION = "fetch-application";
    public static final String FETCH_ROLE = "fetch-role";
    public static final String COMPUTE_FIT_SCORE = "compute-fit-score";
    public static final String FETCH_EMPLOYMENT_VERIFICATION = "fetch-employment-verification";
    public static final String SCHEDULE_INTERVIEW = "schedule-interview";
    public static final String CLOSE_APPLICATION = "close-application";

    private static final Logger LOG = Logger.getLogger(ScreeningTasks.class);

    /** Blind screening: the models never see the candidate's name or contact details. */
    @LHTaskMethod(FETCH_APPLICATION)
    public Map<String, Object> fetchApplication(String applicationId) {
        Map<String, Object> app = Ats.application(applicationId);
        return Map.of(
                "application_id", app.get("application_id"),
                "resume", app.get("resume"),
                "cover_letter", app.get("cover_letter"));
    }

    @LHTaskMethod(FETCH_ROLE)
    public Map<String, Object> fetchRole(String track) {
        return Ats.role(track);
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
        return EmploymentRecords.verify(applicationId);
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
