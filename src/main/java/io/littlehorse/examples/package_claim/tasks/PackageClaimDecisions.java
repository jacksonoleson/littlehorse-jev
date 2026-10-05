package io.littlehorse.examples.package_claim.tasks;

import static io.littlehorse.examples.package_claim.policy.PackageClaimPolicy.*;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.shared.jev.SystemOne.Question;
import io.littlehorse.shared.models.DecisionModels;
import io.littlehorse.shared.models.ModelResponse;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import java.util.Map;

/**
 * The four model decisions in the package-claim workflow. Questions live in
 * {@link io.littlehorse.examples.package_claim.policy.PackageClaimPolicy}. Each returns a flat JSON object that the WfSpec
 * stores as a variable and branches on directly. Every decision exists once per engine.
 */
@LHTask
public class PackageClaimDecisions {

    public static final String CLASSIFY_CLAIM = "classify-claim-";
    public static final String ASSESS_TRACKING = "assess-tracking-";
    public static final String ASSESS_RISK = "assess-risk-";
    public static final String DECIDE_RESOLUTION = "decide-resolution-";

    private final DecisionModels models;

    public PackageClaimDecisions(DecisionModels models) {
        this.models = models;
    }

    @LHTaskMethod(CLASSIFY_CLAIM + JEV)
    public Map<String, Object> classifyClaimJev(String emailBody, Map<String, Object> order, WorkerContext ctx)
            throws Exception {
        return classifyClaim(JEV, emailBody, order, ctx);
    }

    @LHTaskMethod(CLASSIFY_CLAIM + OPENAI)
    public Map<String, Object> classifyClaimOpenAi(String emailBody, Map<String, Object> order, WorkerContext ctx)
            throws Exception {
        return classifyClaim(OPENAI, emailBody, order, ctx);
    }

    @LHTaskMethod(ASSESS_TRACKING + JEV)
    public Map<String, Object> assessTrackingJev(
            String emailBody, Map<String, Object> order, Map<String, Object> tracking, WorkerContext ctx)
            throws Exception {
        return assessTracking(JEV, emailBody, order, tracking, ctx);
    }

    @LHTaskMethod(ASSESS_TRACKING + OPENAI)
    public Map<String, Object> assessTrackingOpenAi(
            String emailBody, Map<String, Object> order, Map<String, Object> tracking, WorkerContext ctx)
            throws Exception {
        return assessTracking(OPENAI, emailBody, order, tracking, ctx);
    }

    @LHTaskMethod(ASSESS_RISK + JEV)
    public Map<String, Object> assessRiskJev(String emailBody, Map<String, Object> history, WorkerContext ctx)
            throws Exception {
        return assessRisk(JEV, emailBody, history, ctx);
    }

    @LHTaskMethod(ASSESS_RISK + OPENAI)
    public Map<String, Object> assessRiskOpenAi(String emailBody, Map<String, Object> history, WorkerContext ctx)
            throws Exception {
        return assessRisk(OPENAI, emailBody, history, ctx);
    }

    @LHTaskMethod(DECIDE_RESOLUTION + JEV)
    public Map<String, Object> decideResolutionJev(
            String emailBody,
            Map<String, Object> order,
            Map<String, Object> tracking,
            Map<String, Object> evidence,
            Map<String, Object> risk,
            WorkerContext ctx)
            throws Exception {
        return decideResolution(JEV, emailBody, order, tracking, evidence, risk, ctx);
    }

    @LHTaskMethod(DECIDE_RESOLUTION + OPENAI)
    public Map<String, Object> decideResolutionOpenAi(
            String emailBody,
            Map<String, Object> order,
            Map<String, Object> tracking,
            Map<String, Object> evidence,
            Map<String, Object> risk,
            WorkerContext ctx)
            throws Exception {
        return decideResolution(OPENAI, emailBody, order, tracking, evidence, risk, ctx);
    }

    private Map<String, Object> classifyClaim(
            String engine, String emailBody, Map<String, Object> order, WorkerContext ctx) throws Exception {
        ModelResponse r = ask(engine, ctx,
                Map.of("customer_email", emailBody, "order", order), CLAIM_QUESTIONS);
        ModelResponse.Answer claim = r.get("claim_type");
        return r.toResult("type", claim.choice(), "confidence", claim.confidence(),
                "probabilities", claim.probabilities());
    }

    private Map<String, Object> assessTracking(
            String engine, String emailBody, Map<String, Object> order, Map<String, Object> tracking,
            WorkerContext ctx) throws Exception {
        ModelResponse r = ask(engine, ctx,
                Map.of("customer_email", emailBody, "order", order, "tracking", tracking), TRACKING_QUESTIONS);
        return r.toResult(
                "delivered", r.get("delivered").noul(),
                "proof_at_address", r.get("proof_at_address").noul(),
                "wrong_location", r.get("wrong_location").noul(),
                "contradicts_customer", r.get("contradicts_customer").noul());
    }

    private Map<String, Object> assessRisk(
            String engine, String emailBody, Map<String, Object> history, WorkerContext ctx) throws Exception {
        ModelResponse r = ask(engine, ctx,
                Map.of("customer_email", emailBody, "claim_history", history), RISK_QUESTIONS);
        ModelResponse.Answer risk = r.get("abuse_risk");
        return r.toResult("abuse_risk", RISK_LEVELS.get((int) Math.round(risk.score())),
                "confidence", risk.confidence(),
                "pressure_tactics", r.get("pressure_tactics").noul());
    }

    private Map<String, Object> decideResolution(
            String engine,
            String emailBody,
            Map<String, Object> order,
            Map<String, Object> tracking,
            Map<String, Object> evidence,
            Map<String, Object> risk,
            WorkerContext ctx)
            throws Exception {
        ModelResponse r = ask(engine, ctx,
                Map.of(
                        "customer_email", emailBody,
                        "order", order,
                        "tracking", tracking,
                        "assessment", Map.of("tracking_evidence", evidence, "customer_risk", risk),
                        "policy", RESOLUTION_POLICY),
                RESOLUTION_QUESTIONS);
        ModelResponse.Answer resolution = r.get("resolution");
        return r.toResult("decision", resolution.choice(), "confidence", resolution.confidence(),
                "probabilities", resolution.probabilities());
    }

    private ModelResponse ask(
            String engine, WorkerContext ctx, Map<String, Object> state, Map<String, Question> questions)
            throws Exception {
        return models.ask(engine, ctx, state, questions);
    }
}
