package io.littlehorse.examples.screening.tasks;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.*;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.shared.jev.SystemOne.Question;
import io.littlehorse.shared.models.DecisionModels;
import io.littlehorse.shared.models.ModelResponse;
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
 * verification report).
 */
@LHTask
public class ScreeningDecisionWorker {

    public static final String TRIAGE_APPLICATION = "triage-application-";
    public static final String CHECK_REQUIREMENTS = "check-requirements-";
    public static final String VERIFY_HISTORY = "verify-history-";
    public static final String RECOMMEND_NEXT_STEP = "recommend-next-step-";

    private final DecisionModels models;

    public ScreeningDecisionWorker(DecisionModels models) {
        this.models = models;
    }

    @LHTaskMethod(TRIAGE_APPLICATION + JEV)
    public Map<String, Object> triageJev(Map<String, Object> application, WorkerContext ctx) throws Exception {
        return triage(JEV, application, ctx);
    }

    @LHTaskMethod(TRIAGE_APPLICATION + OPENAI)
    public Map<String, Object> triageOpenAi(Map<String, Object> application, WorkerContext ctx) throws Exception {
        return triage(OPENAI, application, ctx);
    }

    @LHTaskMethod(CHECK_REQUIREMENTS + JEV)
    public Map<String, Object> checkRequirementsJev(
            Map<String, Object> application, Map<String, Object> role, WorkerContext ctx) throws Exception {
        return checkRequirements(JEV, application, role, ctx);
    }

    @LHTaskMethod(CHECK_REQUIREMENTS + OPENAI)
    public Map<String, Object> checkRequirementsOpenAi(
            Map<String, Object> application, Map<String, Object> role, WorkerContext ctx) throws Exception {
        return checkRequirements(OPENAI, application, role, ctx);
    }

    @LHTaskMethod(VERIFY_HISTORY + JEV)
    public Map<String, Object> verifyHistoryJev(
            Map<String, Object> application, Map<String, Object> verification, WorkerContext ctx) throws Exception {
        return verifyHistory(JEV, application, verification, ctx);
    }

    @LHTaskMethod(VERIFY_HISTORY + OPENAI)
    public Map<String, Object> verifyHistoryOpenAi(
            Map<String, Object> application, Map<String, Object> verification, WorkerContext ctx) throws Exception {
        return verifyHistory(OPENAI, application, verification, ctx);
    }

    @LHTaskMethod(RECOMMEND_NEXT_STEP + JEV)
    public Map<String, Object> recommendJev(
            Map<String, Object> role, Map<String, Object> fit, Map<String, Object> requirements,
            Map<String, Object> history, WorkerContext ctx) throws Exception {
        return recommend(JEV, role, fit, requirements, history, ctx);
    }

    @LHTaskMethod(RECOMMEND_NEXT_STEP + OPENAI)
    public Map<String, Object> recommendOpenAi(
            Map<String, Object> role, Map<String, Object> fit, Map<String, Object> requirements,
            Map<String, Object> history, WorkerContext ctx) throws Exception {
        return recommend(OPENAI, role, fit, requirements, history, ctx);
    }

    /** Intent routing + speculative fan-out: route to a track and score every dimension in one call. */
    private Map<String, Object> triage(String engine, Map<String, Object> application, WorkerContext ctx)
            throws Exception {
        ModelResponse r = models.ask(engine, ctx, Map.of("application", application), TRIAGE_QUESTIONS);
        Map<String, Object> scores = new LinkedHashMap<>();
        DIMENSIONS.keySet().forEach(d -> scores.put(d, r.get(d).score()));
        ModelResponse.Answer track = r.get("track");
        return r.toResult(
                "track", track.choice(),
                "track_confidence", track.confidence(),
                "track_probabilities", track.probabilities(),
                "seniority", r.get("seniority").score(),
                "spam", r.get("spam").noul(),
                "remote_only", r.get("remote_only").noul(),
                "scores", scores);
    }

    /** One Noul per requirement, built from the role the previous answer selected. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkRequirements(
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
        return r.toResult("must_haves", must, "min_must_have", minMustHave, "nice_to_haves", nice);
    }

    private Map<String, Object> verifyHistory(
            String engine, Map<String, Object> application, Map<String, Object> verification, WorkerContext ctx)
            throws Exception {
        Object experience = ((Map<?, ?>) application.get("resume")).get("experience");
        ModelResponse r = models.ask(engine, ctx,
                Map.of("resume_experience", experience, "employment_verification", verification),
                VERIFY_QUESTIONS);
        ModelResponse.Answer discrepancy = r.get("discrepancy");
        return r.toResult(
                "employers_match", r.get("employers_match").noul(),
                "titles_match", r.get("titles_match").noul(),
                "dates_match", r.get("dates_match").noul(),
                "discrepancy", discrepancy.choice(),
                "discrepancy_confidence", discrepancy.confidence());
    }

    /** Every earlier answer (and the code-computed composite) goes into the state of the final call. */
    private Map<String, Object> recommend(
            String engine, Map<String, Object> role, Map<String, Object> fit, Map<String, Object> requirements,
            Map<String, Object> history, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of(
                        "role", Map.of("title", role.get("title"), "must_haves", role.get("must_haves")),
                        "screening", Map.of("fit", fit, "requirements", requirements, "verification", history)),
                NEXT_STEP_QUESTIONS);
        ModelResponse.Answer next = r.get("next_step");
        return r.toResult("decision", next.choice(), "confidence", next.confidence(),
                "probabilities", next.probabilities());
    }
}
