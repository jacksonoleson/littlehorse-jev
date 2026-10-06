package io.littlehorse.examples.screening.tasks;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.*;

import io.littlehorse.common.jev.SystemOne.Question;
import io.littlehorse.common.models.DecisionModels;
import io.littlehorse.common.models.ModelResponse;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four model calls in candidate screening. Questions live in
 * {@link io.littlehorse.examples.screening.policy.ScreeningPolicy}. Each call asks many questions in parallel; a new
 * call is made only when it needs data the previous answer unlocked (a role's requirements, a
 * verification report). Unlike the other examples, these return {@code JSON_OBJ}: their shape depends on the
 * role (a requirement list of any length, a map of skill scores).
 */
@LHTask
public class ScreeningDecisionWorker {

    public static final String TRIAGE_APPLICATION = "triage-application";
    public static final String CHECK_REQUIREMENTS = "check-requirements";
    public static final String VERIFY_HISTORY = "verify-history";
    public static final String RECOMMEND_NEXT_STEP = "recommend-next-step";

    private final DecisionModels models;

    public ScreeningDecisionWorker(DecisionModels models) {
        this.models = models;
    }

    /** Intent routing + speculative fan-out: route to a track and score every dimension in one call. */
    @LHTaskMethod(TRIAGE_APPLICATION)
    public Map<String, Object> triage(String engine, Map<String, Object> application, WorkerContext ctx)
            throws Exception {
        ModelResponse r = models.ask(engine, ctx, Map.of("application", application), TRIAGE_QUESTIONS);
        Map<String, Object> scores = new LinkedHashMap<>();
        DIMENSIONS.keySet().forEach(d -> scores.put(d, r.get(d).score()));
        ModelResponse.Answer track = r.get("track");
        return Map.of(
                "track", track.choice(),
                "track_confidence", track.confidence(),
                "seniority", r.get("seniority").score(),
                "spam", r.get("spam").noul(),
                "remote_only", r.get("remote_only").noul(),
                "scores", scores,
                "model", r.model());
    }

    /** One Noul per requirement, built from the role the previous answer selected. */
    @LHTaskMethod(CHECK_REQUIREMENTS)
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkRequirements(
            String engine, Map<String, Object> application, Map<String, Object> role, WorkerContext ctx)
            throws Exception {
        List<String> mustHaves = (List<String>) role.get("must_haves");
        List<String> niceToHaves = (List<String>) role.get("nice_to_haves");
        Map<String, Question> questions = new LinkedHashMap<>();
        for (int i = 0; i < mustHaves.size(); i++) {
            questions.put("must_have_" + i, requirementQuestion(mustHaves.get(i)));
        }
        for (int i = 0; i < niceToHaves.size(); i++) {
            questions.put("nice_to_have_" + i, requirementQuestion(niceToHaves.get(i)));
        }

        ModelResponse r = models.ask(engine, ctx, Map.of("application", application), questions);
        List<Map<String, Object>> must = new ArrayList<>();
        double minMustHave = 1.0;
        for (int i = 0; i < mustHaves.size(); i++) {
            double met = r.get("must_have_" + i).noul();
            minMustHave = Math.min(minMustHave, met);
            must.add(Map.of("requirement", mustHaves.get(i), "met", met));
        }
        List<Map<String, Object>> nice = new ArrayList<>();
        for (int i = 0; i < niceToHaves.size(); i++) {
            nice.add(Map.of("requirement", niceToHaves.get(i), "met", r.get("nice_to_have_" + i).noul()));
        }
        return Map.of("must_haves", must, "min_must_have", minMustHave, "nice_to_haves", nice, "model", r.model());
    }

    @LHTaskMethod(VERIFY_HISTORY)
    public Map<String, Object> verifyHistory(
            String engine, Map<String, Object> application, Map<String, Object> verification, WorkerContext ctx)
            throws Exception {
        Object experience = ((Map<?, ?>) application.get("resume")).get("experience");
        ModelResponse r = models.ask(engine, ctx,
                Map.of("resume_experience", experience, "employment_verification", verification),
                VERIFY_QUESTIONS);
        ModelResponse.Answer discrepancy = r.get("discrepancy");
        return Map.of(
                "employers_match", r.get("employers_match").noul(),
                "titles_match", r.get("titles_match").noul(),
                "dates_match", r.get("dates_match").noul(),
                "discrepancy", discrepancy.choice(),
                "discrepancy_confidence", discrepancy.confidence(),
                "model", r.model());
    }

    /** Every earlier answer (and the code-computed composite) goes into the state of the final call. */
    @LHTaskMethod(RECOMMEND_NEXT_STEP)
    public Map<String, Object> recommend(
            String engine, Map<String, Object> role, Map<String, Object> fit, Map<String, Object> requirements,
            Map<String, Object> history, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of(
                        "role", Map.of("title", role.get("title"), "must_haves", role.get("must_haves")),
                        "screening", Map.of("fit", fit, "requirements", requirements, "verification", history)),
                NEXT_STEP_QUESTIONS);
        ModelResponse.Answer next = r.get("next_step");
        return Map.of("decision", next.choice(), "confidence", next.confidence(), "model", r.model());
    }
}
