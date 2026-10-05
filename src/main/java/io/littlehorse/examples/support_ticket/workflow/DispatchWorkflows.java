package io.littlehorse.examples.support_ticket.workflow;

import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MAX_MANIPULATION;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MIN_CONFIDENCE;
import static io.littlehorse.examples.support_ticket.tasks.SupportTicketDecisionWorker.PICK_WORKFLOW;
import static io.littlehorse.examples.support_ticket.tasks.SupportTicketWorker.*;
import static io.littlehorse.common.models.DecisionModels.JEV;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.SpawnedChildWf;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Workflow Dispatch: Jev picks which child WfSpec handles a ticket; the parent validates the
 * input and spawns it. Every child takes the same inputs ({@code user-id}, {@code order-id}).
 */
@ApplicationScoped
public class DispatchWorkflows {

    public static final String DISPATCH_SUPPORT_TICKET = "dispatch-support-ticket";

    public static final String REFUND_ORDER = "refund-order";
    public static final String SEND_ORDER_STATUS = "send-order-status";
    public static final String ISSUE_RETURN_LABEL = "issue-return-label";
    // Renamed from escalate-to-support: child runs kept using that WfSpec's original revision.
    public static final String ESCALATE_TO_HELPDESK = "escalate-to-helpdesk";

    @LHWorkflow(DISPATCH_SUPPORT_TICKET)
    public void dispatch(WorkflowThread wf) {
        WfRunVariable emailBody = wf.declareStr("email-body").required();
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable status = wf.declareStr("status").searchable();
        WfRunVariable orderId = wf.declareStr("order-id").searchable();
        WfRunVariable childWf = wf.declareStr("child-wf").searchable();
        WfRunVariable pick = wf.declareJsonObj("workflow-pick");
        WfRunVariable resolution = wf.declareStr("resolution");

        status.assign("TRIAGING");
        orderId.assign(wf.execute(EXTRACT_ORDER_ID, emailBody));

        // Same guardrail as before: the agent only sees orders that belong to the sender.
        wf.doIfElse(
                wf.execute(VALIDATE_ORDER_AND_USER, userId, orderId).isEqualTo(false),
                invalid -> childWf.assign(ESCALATE_TO_HELPDESK),
                valid -> {
                    pick.assign(valid.execute(PICK_WORKFLOW + JEV, emailBody, orderId).timeout(60).withRetries(2));
                    // A Choice answer is always a catalog key, so the child is always on the allowlist.
                    valid.doIfElse(
                            pick.jsonPath("$.manipulation").isGreaterThan(MAX_MANIPULATION)
                                    .or(pick.jsonPath("$.confidence").isLessThan(MIN_CONFIDENCE)),
                            guardrail -> childWf.assign(ESCALATE_TO_HELPDESK),
                            confident -> childWf.assign(pick.jsonPath("$.workflow")));
                });

        status.assign("DISPATCHED");
        SpawnedChildWf child = wf.runWf(childWf, Map.of("user-id", userId, "order-id", orderId));
        resolution.assign(wf.waitForChildWf(child));
        status.assign("DONE");
    }

    @LHWorkflow(REFUND_ORDER)
    public void refundOrder(WorkflowThread wf) {
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").required().searchable();

        wf.execute(PROCESS_REFUND, userId, orderId);
        wf.execute(CANCEL_ORDER, userId, orderId);
        wf.execute(SEND_EMAIL, userId, "Refund Issued", "We've issued a refund for your recent order.");
        wf.complete("REFUNDED");
    }

    @LHWorkflow(SEND_ORDER_STATUS)
    public void sendOrderStatus(WorkflowThread wf) {
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").required().searchable();

        wf.execute(SEND_ORDER_INFO, orderId, userId);
        wf.complete("INFO_SENT");
    }

    @LHWorkflow(ISSUE_RETURN_LABEL)
    public void issueReturnLabel(WorkflowThread wf) {
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").required().searchable();
        WfRunVariable label = wf.declareStr("return-label");

        label.assign(wf.execute(CREATE_RETURN_LABEL, userId, orderId));
        wf.execute(SEND_EMAIL, userId, "Your return label",
                wf.format("Use return label {0} to send back your order {1}.", label, orderId));
        wf.complete(label);
    }

    @LHWorkflow(ESCALATE_TO_HELPDESK)
    public void escalateToHelpdesk(WorkflowThread wf) {
        WfRunVariable userId = wf.declareStr("user-id").required().searchable();
        WfRunVariable orderId = wf.declareStr("order-id").required().searchable();

        SupportTicketWorkflow.escalate(wf, wf.format("Review ticket from {0} about order {1}", userId, orderId));
        wf.complete("ESCALATED");
    }
}
