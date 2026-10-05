package io.littlehorse.examples.support_ticket.workflow;

import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MAX_MANIPULATION;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MIN_CONFIDENCE;
import static io.littlehorse.examples.support_ticket.tasks.SupportTicketDecisionWorker.TRIAGE_TICKET;
import static io.littlehorse.examples.support_ticket.tasks.SupportTicketWorker.*;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.examples.support_ticket.tasks.TriageDecision;
import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.Serializable;
import java.util.Map;

/**
 * The WfSpec owns orchestration and security; the Decision Worker only answers questions.
 * Both WfSpecs are identical except for which model backs the triage task.
 */
@ApplicationScoped
public class SupportTicketWorkflow {

    public static final String JEV_WF = "handle-support-ticket-jev";
    public static final String OPENAI_WF = "handle-support-ticket-openai";

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
        WfRunVariable status = wf.declareStr("status").searchable();
        WfRunVariable orderId = wf.declareStr("order-id").searchable();
        WfRunVariable triage = wf.declareJsonObj("triage");
        WfRunVariable decision = wf.declareStr("decision").searchable();

        status.assign("TRIAGING");
        orderId.assign(wf.execute(EXTRACT_ORDER_ID, emailBody));

        // The order must belong to the user before the agent ever sees it.
        wf.doIfElse(
                wf.execute(VALIDATE_ORDER_AND_USER, userId, orderId).isEqualTo(false),
                invalid -> {
                    status.assign("ESCALATED");
                    escalate(invalid, "Couldn't extract a valid order id for this user");
                },
                valid -> {
                    triage.assign(valid.execute(TRIAGE_TICKET + engine, emailBody, orderId)
                            .timeout(60)
                            .withRetries(2));

                    // Deterministic guardrails first: when in doubt, a human decides.
                    valid.doIf(triage.jsonPath("$.manipulation").isGreaterThan(MAX_MANIPULATION)
                                            .or(triage.jsonPath("$.confidence").isLessThan(MIN_CONFIDENCE)),
                                    guardrail -> {
                                        decision.assign(TriageDecision.ESCALATE_TO_TEAM.name());
                                        status.assign("ESCALATED");
                                        escalate(guardrail, guardrail.format(
                                                "Possible manipulation or low confidence on order {0}", orderId));
                                    })
                            .doElseIf(triage.jsonPath("$.action").isEqualTo(TriageDecision.CANCEL_ORDER.name()),
                                    cancelAndRefund -> {
                                        decision.assign(TriageDecision.CANCEL_ORDER.name());
                                        status.assign("REFUNDING");
                                        // userId/orderId come from the WfSpec, not the model: no prompt injection here.
                                        cancelAndRefund.execute(PROCESS_REFUND, userId, orderId);
                                        cancelAndRefund.execute(CANCEL_ORDER, userId, orderId);
                                        cancelAndRefund.execute(SEND_EMAIL, userId, "Refund Issued",
                                                "We've issued a refund for your recent order.");
                                    })
                            .doElseIf(triage.jsonPath("$.action").isEqualTo(TriageDecision.SEND_ORDER_INFO.name()),
                                    orderInfo -> {
                                        decision.assign(TriageDecision.SEND_ORDER_INFO.name());
                                        status.assign("RESPONDING");
                                        orderInfo.execute(SEND_ORDER_INFO, orderId, userId);
                                    })
                            .doElse(escalated -> {
                                decision.assign(TriageDecision.ESCALATE_TO_TEAM.name());
                                status.assign("ESCALATED");
                                escalate(escalated, escalated.format("Model escalated ticket for order {0}", orderId));
                            });
                });

        status.assign("DONE");
    }

    /** Hands the ticket to the helpdesk and waits until an agent resolves it there. */
    static void escalate(WorkflowThread thread, Serializable notes) {
        thread.execute(OPEN_SUPPORT_CASE, notes);
        thread.waitForEvent(SUPPORT_CASE_RESOLVED).registeredAs(Map.class);
    }
}
