package io.littlehorse.examples.support_ticket.tasks;

import static io.littlehorse.examples.support_ticket.policy.DispatchPolicy.DISPATCH_QUESTIONS;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.TRIAGE_QUESTIONS;
import static io.littlehorse.common.models.DecisionModels.JEV;

import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.common.models.DecisionModels;
import io.littlehorse.common.models.ModelResponse;
import io.littlehorse.common.orders.Order;
import io.littlehorse.common.orders.OrderStore;
import io.littlehorse.examples.support_ticket.structs.TicketTriage;
import io.littlehorse.examples.support_ticket.structs.WorkflowPick;
import java.util.Map;

/**
 * Decision Workers for support tickets: read-only access to orders and no ability to act. Each returns a
 * LittleHorse Struct; the WfSpec applies the confidence and manipulation gates and takes the action.
 */
@LHTask
public class SupportTicketDecisionWorker {

    public static final String TRIAGE_TICKET = "triage-ticket";
    public static final String PICK_WORKFLOW = "pick-workflow";

    private final DecisionModels models;
    private final OrderStore orders;

    public SupportTicketDecisionWorker(DecisionModels models, OrderStore orders) {
        this.models = models;
        this.orders = orders;
    }

    @LHTaskMethod(TRIAGE_TICKET)
    public TicketTriage triage(String engine, String emailBody, String orderId, WorkerContext ctx)
            throws Exception {
        ModelResponse r = models.ask(engine, ctx, state(emailBody, orderId), TRIAGE_QUESTIONS);
        ModelResponse.Answer action = r.get("action");
        return new TicketTriage(action.choice(), action.confidence(), r.get("manipulation").noul(), r.model());
    }

    /** Workflow dispatch (Jev only): the choice is the name of the child WfSpec to run. */
    @LHTaskMethod(PICK_WORKFLOW)
    public WorkflowPick pickWorkflow(String emailBody, String orderId, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(JEV, ctx, state(emailBody, orderId), DISPATCH_QUESTIONS);
        ModelResponse.Answer workflow = r.get("workflow");
        return new WorkflowPick(workflow.choice(), workflow.confidence(), r.get("manipulation").noul(), r.model());
    }

    /** The untrusted email plus read-only order data. */
    private Map<String, Object> state(String emailBody, String orderId) {
        Order order = orders.findById(orderId).orElseThrow();
        return Map.of(
                "customer_email", emailBody,
                "order", Map.of(
                        "status", order.status(),
                        "items", order.items(),
                        "total", order.total().toPlainString()));
    }
}
