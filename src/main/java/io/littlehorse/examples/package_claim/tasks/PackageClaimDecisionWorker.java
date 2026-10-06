package io.littlehorse.examples.package_claim.tasks;

import static io.littlehorse.examples.package_claim.policy.PackageClaimPolicy.*;

import io.littlehorse.common.models.DecisionModels;
import io.littlehorse.common.models.ModelResponse;
import io.littlehorse.examples.package_claim.structs.ClaimClassification;
import io.littlehorse.examples.package_claim.structs.ClaimResolution;
import io.littlehorse.examples.package_claim.structs.CustomerRisk;
import io.littlehorse.examples.package_claim.structs.TrackingEvidence;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import java.util.Map;

/**
 * The four model decisions in the package-claim workflow. Questions live in
 * {@link io.littlehorse.examples.package_claim.policy.PackageClaimPolicy}. Each returns a LittleHorse Struct that the
 * WfSpec stores as a variable, branches on, and passes into later decisions. {@code engine} picks the model.
 */
@LHTask
public class PackageClaimDecisionWorker {

    public static final String CLASSIFY_CLAIM = "classify-claim";
    public static final String ASSESS_TRACKING = "assess-tracking";
    public static final String ASSESS_RISK = "assess-risk";
    public static final String DECIDE_RESOLUTION = "decide-resolution";

    private final DecisionModels models;

    public PackageClaimDecisionWorker(DecisionModels models) {
        this.models = models;
    }

    @LHTaskMethod(CLASSIFY_CLAIM)
    public ClaimClassification classifyClaim(
            String engine, String emailBody, Map<String, Object> order, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of("customer_email", emailBody, "order", order), CLAIM_QUESTIONS);
        ModelResponse.Answer claim = r.get("claim_type");
        return new ClaimClassification(claim.choice(), claim.confidence(), r.model());
    }

    @LHTaskMethod(ASSESS_TRACKING)
    public TrackingEvidence assessTracking(
            String engine, String emailBody, Map<String, Object> order, Map<String, Object> tracking,
            WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of("customer_email", emailBody, "order", order, "tracking", tracking), TRACKING_QUESTIONS);
        return new TrackingEvidence(
                r.get("delivered").noul(),
                r.get("proof_at_address").noul(),
                r.get("wrong_location").noul(),
                r.get("contradicts_customer").noul(),
                r.model());
    }

    @LHTaskMethod(ASSESS_RISK)
    public CustomerRisk assessRisk(
            String engine, String emailBody, Map<String, Object> history, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of("customer_email", emailBody, "claim_history", history), RISK_QUESTIONS);
        ModelResponse.Answer risk = r.get("abuse_risk");
        return new CustomerRisk(RISK_LEVELS.get((int) Math.round(risk.score())), risk.confidence(),
                r.get("pressure_tactics").noul(), r.model());
    }

    @LHTaskMethod(DECIDE_RESOLUTION)
    public ClaimResolution decideResolution(
            String engine,
            String emailBody,
            Map<String, Object> order,
            Map<String, Object> tracking,
            TrackingEvidence evidence,
            CustomerRisk risk,
            WorkerContext ctx)
            throws Exception {
        ModelResponse r = models.ask(engine, ctx,
                Map.of(
                        "customer_email", emailBody,
                        "order", order,
                        "tracking", tracking,
                        "assessment", Map.of("tracking_evidence", evidence, "customer_risk", risk),
                        "policy", RESOLUTION_POLICY),
                RESOLUTION_QUESTIONS);
        ModelResponse.Answer resolution = r.get("resolution");
        return new ClaimResolution(resolution.choice(), resolution.confidence(), r.model());
    }
}
