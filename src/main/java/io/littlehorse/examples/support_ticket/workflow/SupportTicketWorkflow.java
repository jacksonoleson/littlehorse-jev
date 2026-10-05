package io.littlehorse.examples.support_ticket.workflow;

import static io.littlehorse.examples.support_ticket.tasks.SupportTicketTasks.*;

import io.littlehorse.examples.support_ticket.tasks.JevDecisionWorker;
import io.littlehorse.examples.support_ticket.tasks.OpenAiDecisionWorker;
import io.littlehorse.examples.support_ticket.tasks.TriageDecision;
import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.Serializable;
import java.util.Map;

/**
 * The WfSpec owns orchestration and security; the Decision Worker only picks a path.
 * Both WfSpecs are identical except for which model backs the triage task.
 */
@ApplicationScoped
public class SupportTicketWorkflow {

    public static final String JEV_WF = "handle-support-ticket-jev";
    public static final String OPENAI_WF = "handle-support-ticket-openai";

    @LHWorkflow(JEV_WF)
    public void jev(WorkflowThread wf) {
        define(wf, JevDecisionWorker.TRIAGE_TASK);
    }

    @LHWorkflow(OPENAI_WF)
    public void openAi(WorkflowThread wf) {
        define(wf, OpenAiDecisionWorker.TRIAGE_TASK);
    }

    private void define(WorkflowThread wf, String triageTask) {
        WfRunVariable emailBody = wf.declareStr("email-body").required();
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable status = wf.declareStr("status").searchable();
        WfRunVariable orderId = wf.declareStr("order-id").searchable();
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
                    decision.assign(valid.execute(triageTask, emailBody, orderId)
                            .timeout(60)
                            .withRetries(2));

                    valid.doIf(decision.isEqualTo(TriageDecision.CANCEL_ORDER.name()), cancelAndRefund -> {
                                status.assign("REFUNDING");
                                // userId/orderId come from the WfSpec, not the agent: no prompt injection here.
                                cancelAndRefund.execute(PROCESS_REFUND, userId, orderId);
                                cancelAndRefund.execute(CANCEL_ORDER, userId, orderId);
                                cancelAndRefund.execute(SEND_EMAIL, userId, "Refund Issued",
                                        "We've issued a refund for your recent order.");
                            })
                            .doElseIf(decision.isEqualTo(TriageDecision.SEND_ORDER_INFO.name()), orderInfo -> {
                                status.assign("RESPONDING");
                                orderInfo.execute(SEND_ORDER_INFO, orderId, userId);
                            })
                            .doElse(escalated -> {
                                status.assign("ESCALATED");
                                escalate(escalated, escalated.format("Agent escalated ticket for order {0}", orderId));
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
