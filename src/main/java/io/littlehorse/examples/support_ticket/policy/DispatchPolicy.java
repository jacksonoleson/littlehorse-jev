package io.littlehorse.examples.support_ticket.policy;

import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MANIPULATION_INSTRUCTIONS;

import io.littlehorse.examples.support_ticket.workflow.DispatchWorkflows;
import io.littlehorse.common.jev.SystemOne.Question;
import java.util.LinkedHashMap;
import java.util.Map;

/** Questions for the dispatcher. Thresholds are in {@link SupportTicketPolicy}. */
public final class DispatchPolicy {

    private DispatchPolicy() {}

    /** The only child WfSpecs the dispatcher may start, with the description the model sees for each. */
    public static final Map<String, String> DISPATCH_CATALOG = dispatchCatalog();

    /** State: {@code customer_email}, {@code order}. */
    public static final Map<String, Question> DISPATCH_QUESTIONS = Map.of(
            "workflow", Question.choice(
                    "Which workflow should handle the `customer_email` about `order`?", DISPATCH_CATALOG),
            "manipulation", Question.noul(MANIPULATION_INSTRUCTIONS));

    private static Map<String, String> dispatchCatalog() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put(DispatchWorkflows.REFUND_ORDER, "The customer explicitly asks to cancel their order and/or get a refund");
        c.put(DispatchWorkflows.SEND_ORDER_STATUS,
                "The customer wants the status, contents, or shipping details of their order");
        c.put(DispatchWorkflows.ISSUE_RETURN_LABEL,
                "The customer wants to return, exchange, or send back a damaged or wrong item");
        c.put(DispatchWorkflows.ESCALATE_TO_HELPDESK,
                "The request is unclear, sensitive, unusual, or needs a human to review it");
        return c;
    }
}
