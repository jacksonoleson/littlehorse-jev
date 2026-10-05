package io.littlehorse.examples.package_claim.workflow;

import static io.littlehorse.examples.package_claim.policy.PackageClaimPolicy.*;
import static io.littlehorse.examples.package_claim.tasks.PackageClaimDecisionWorker.*;
import static io.littlehorse.examples.package_claim.tasks.PackageClaimWorker.*;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.TaskNodeOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * "Where's my package?" claims: four chained model decisions, each feeding typed output into
 * workflow variables that the next step and the WfSpec's conditions read directly.
 * Both WfSpecs are identical except for which engine answers the questions.
 */
@ApplicationScoped
public class PackageClaimWorkflow {

    public static final String JEV_WF = "package-claim-jev";
    public static final String OPENAI_WF = "package-claim-openai";

    @LHWorkflow(JEV_WF)
    public void jev(WorkflowThread wf) {
        define(wf, JEV);
    }

    @LHWorkflow(OPENAI_WF)
    public void openAi(WorkflowThread wf) {
        define(wf, OPENAI);
    }

    private void define(WorkflowThread wf, String engine) {
        WfRunVariable emailBody = wf.declareStr("email-body").required();
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").searchable();
        WfRunVariable outcome = wf.declareStr("outcome").searchable();
        WfRunVariable order = wf.declareJsonObj("order");
        WfRunVariable claim = wf.declareJsonObj("claim");
        WfRunVariable tracking = wf.declareJsonObj("tracking");
        WfRunVariable evidence = wf.declareJsonObj("evidence");
        WfRunVariable history = wf.declareJsonObj("claim-history");
        WfRunVariable risk = wf.declareJsonObj("risk");
        WfRunVariable resolution = wf.declareJsonObj("resolution");

        outcome.assign("TRIAGING");
        order.assign(wf.execute(FIND_CLAIMED_ORDER, userId, emailBody));
        orderId.assign(order.jsonPath("$.order_id"));

        wf.doIfElse(
                orderId.isEqualTo(""),
                invalid -> escalate(invalid, outcome, "Couldn't match an order to this customer"),
                valid -> {
                    // Decision 1: what kind of claim is this?
                    claim.assign(decide(valid.execute(CLASSIFY_CLAIM + engine, emailBody, order)));

                    valid.doIf(claim.jsonPath("$.confidence").isLessThan(MIN_CLAIM_CONFIDENCE),
                                    unsureClaim -> escalate(unsureClaim, outcome, "Unsure what the customer is reporting"))
                            .doElseIf(claim.jsonPath("$.type").isEqualTo("DAMAGED_OR_WRONG_ITEM"), damaged -> {
                                outcome.assign("RETURN_LABEL");
                                damaged.execute(SEND_RETURN_LABEL, userId, orderId);
                            })
                            .doElseIf(claim.jsonPath("$.type").isEqualTo("MISSING_PACKAGE"), missing -> {
                                tracking.assign(missing.execute(FETCH_TRACKING, orderId));

                                // Decision 2: what does the carrier's evidence say?
                                evidence.assign(decide(missing.execute(
                                        ASSESS_TRACKING + engine, emailBody, order, tracking)));

                                missing.doIf(evidence.jsonPath("$.proof_at_address").isGreaterThan(CONTRADICTION)
                                                        .and(evidence.jsonPath("$.wrong_location")
                                                                .isGreaterThan(CONTRADICTION)),
                                                contradictory -> escalate(contradictory, outcome,
                                                        "Contradictory tracking evidence"))
                                        .doElseIf(evidence.jsonPath("$.delivered").isLessThan(DELIVERED), inTransit -> {
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
                                                    delivered.execute(ASSESS_RISK + engine, emailBody, history)));

                                            // Decision 4: what does policy call for, given everything so far?
                                            resolution.assign(decide(delivered.execute(DECIDE_RESOLUTION + engine,
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
        thread.doIf(resolution.jsonPath("$.confidence").isLessThan(MIN_RESOLUTION_CONFIDENCE),
                        unsure -> escalate(unsure, outcome, "Low-confidence resolution"))
                .doElseIf(resolution.jsonPath("$.decision").isEqualTo("REFUND"),
                        refunding -> refund(refunding, outcome, userId, orderId))
                .doElseIf(resolution.jsonPath("$.decision").isEqualTo("RESHIP"), reshipping -> {
                    outcome.assign("RESHIPPED");
                    reshipping.execute(RESHIP_ORDER, userId, orderId);
                })
                .doElseIf(resolution.jsonPath("$.decision").isEqualTo("DENY"), denying -> {
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
