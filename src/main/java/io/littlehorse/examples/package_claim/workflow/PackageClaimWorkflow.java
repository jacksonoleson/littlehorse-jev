package io.littlehorse.examples.package_claim.workflow;

import static io.littlehorse.examples.package_claim.policy.PackageClaimPolicy.*;
import static io.littlehorse.examples.package_claim.tasks.PackageClaimDecisionWorker.*;
import static io.littlehorse.examples.package_claim.tasks.PackageClaimWorker.*;
import static io.littlehorse.common.models.DecisionModels.JEV;

import io.littlehorse.examples.package_claim.structs.ClaimClassification;
import io.littlehorse.examples.package_claim.structs.ClaimResolution;
import io.littlehorse.examples.package_claim.structs.CustomerRisk;
import io.littlehorse.examples.package_claim.structs.TrackingEvidence;
import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.TaskNodeOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * "Where's my package?" claims: four chained model decisions. Each returns a typed Struct that the WfSpec's
 * conditions read field by field and that later decisions take as input.
 * The {@code engine} input picks which model answers: {@code jev} (default) or {@code openai}.
 */
@ApplicationScoped
public class PackageClaimWorkflow {

    public static final String PACKAGE_CLAIM = "package-claim";

    @LHWorkflow(PACKAGE_CLAIM)
    public void define(WorkflowThread wf) {
        WfRunVariable engine = wf.declareStr("engine").withDefault(JEV).searchable();
        WfRunVariable emailBody = wf.declareStr("email-body").required();
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").searchable();
        WfRunVariable outcome = wf.declareStr("outcome").searchable();
        WfRunVariable order = wf.declareJsonObj("order");
        WfRunVariable claim = wf.declareStruct("claim", ClaimClassification.class);
        WfRunVariable tracking = wf.declareJsonObj("tracking");
        WfRunVariable evidence = wf.declareStruct("evidence", TrackingEvidence.class);
        WfRunVariable history = wf.declareJsonObj("claim-history");
        WfRunVariable risk = wf.declareStruct("risk", CustomerRisk.class);
        WfRunVariable resolution = wf.declareStruct("resolution", ClaimResolution.class);

        outcome.assign("TRIAGING");
        order.assign(wf.execute(FIND_CLAIMED_ORDER, userId, emailBody));
        orderId.assign(order.jsonPath("$.order_id"));

        wf.doIfElse(
                orderId.isEqualTo(""),
                invalid -> escalate(invalid, outcome, "Couldn't match an order to this customer"),
                valid -> {
                    // Decision 1: what kind of claim is this?
                    claim.assign(decide(valid.execute(CLASSIFY_CLAIM, engine, emailBody, order)));

                    valid.doIf(claim.get("confidence").isLessThan(MIN_CLAIM_CONFIDENCE),
                                    unsureClaim -> escalate(unsureClaim, outcome, "Unsure what the customer is reporting"))
                            .doElseIf(claim.get("type").isEqualTo("DAMAGED_OR_WRONG_ITEM"), damaged -> {
                                outcome.assign("RETURN_LABEL");
                                damaged.execute(SEND_RETURN_LABEL, userId, orderId);
                            })
                            .doElseIf(claim.get("type").isEqualTo("MISSING_PACKAGE"), missing -> {
                                tracking.assign(missing.execute(FETCH_TRACKING, orderId));

                                // Decision 2: what does the carrier's evidence say?
                                evidence.assign(decide(missing.execute(
                                        ASSESS_TRACKING, engine, emailBody, order, tracking)));

                                missing.doIf(evidence.get("proofAtAddress").isGreaterThan(CONTRADICTION)
                                                        .and(evidence.get("wrongLocation")
                                                                .isGreaterThan(CONTRADICTION)),
                                                contradictory -> escalate(contradictory, outcome,
                                                        "Contradictory tracking evidence"))
                                        .doElseIf(evidence.get("delivered").isLessThan(DELIVERED), inTransit -> {
                                            outcome.assign("IN_TRANSIT");
                                            inTransit.execute(NOTIFY_IN_TRANSIT, userId, tracking);
                                        })
                                        // Cheaper to refund than to review.
                                        .doElseIf(order.jsonPath("$.total").isLessThan(AUTO_REFUND_BELOW_USD),
                                                smallOrder -> refund(smallOrder, outcome, userId, orderId))
                                        .doElse(delivered -> {
                                            history.assign(delivered.execute(FETCH_CLAIM_HISTORY, userId));

                                            // Decision 3: is this customer likely abusing claims?
                                            risk.assign(decide(
                                                    delivered.execute(ASSESS_RISK, engine, emailBody, history)));

                                            // Decision 4: what does policy call for, given everything so far?
                                            resolution.assign(decide(delivered.execute(DECIDE_RESOLUTION, engine,
                                                    emailBody, order, tracking, evidence, risk)));

                                            act(delivered, resolution, outcome, userId, orderId, tracking);
                                        });
                            })
                            .doElse(notAClaim -> escalate(notAClaim, outcome, "Not a package claim"));
                });
    }

    private static void act(
            WorkflowThread thread,
            WfRunVariable resolution,
            WfRunVariable outcome,
            WfRunVariable userId,
            WfRunVariable orderId,
            WfRunVariable tracking) {
        // Money moves only on high confidence.
        thread.doIf(resolution.get("confidence").isLessThan(MIN_RESOLUTION_CONFIDENCE),
                        unsure -> escalate(unsure, outcome, "Low-confidence resolution"))
                .doElseIf(resolution.get("decision").isEqualTo("REFUND"),
                        refunding -> refund(refunding, outcome, userId, orderId))
                .doElseIf(resolution.get("decision").isEqualTo("RESHIP"), reshipping -> {
                    outcome.assign("RESHIPPED");
                    reshipping.execute(RESHIP_ORDER, userId, orderId);
                })
                .doElseIf(resolution.get("decision").isEqualTo("DENY"), denying -> {
                    outcome.assign("DENIED");
                    denying.execute(SEND_DELIVERY_EVIDENCE, userId, tracking);
                })
                .doElse(policyUnclear -> escalate(policyUnclear, outcome, "Policy doesn't clearly apply"));
    }

    private static TaskNodeOutput decide(TaskNodeOutput task) {
        return task.timeout(60).withRetries(2);
    }

    private static void refund(
            WorkflowThread thread, WfRunVariable outcome, WfRunVariable userId, WfRunVariable orderId) {
        outcome.assign("REFUNDED");
        thread.execute(REFUND_CLAIM, userId, orderId);
    }

    // A human reviews it in the helpdesk; the WfRun resumes when the helpdesk posts the event back.
    private static void escalate(WorkflowThread thread, WfRunVariable outcome, String reason) {
        outcome.assign("HUMAN_REVIEW");
        thread.execute(OPEN_CLAIM_REVIEW, reason);
        thread.waitForEvent(CLAIM_REVIEW_COMPLETED).registeredAs(Map.class);
    }
}
