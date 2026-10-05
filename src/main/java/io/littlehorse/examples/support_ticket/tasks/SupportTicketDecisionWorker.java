package io.littlehorse.examples.support_ticket.tasks;

import static io.littlehorse.examples.support_ticket.policy.DispatchPolicy.DISPATCH_QUESTIONS;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.TRIAGE_QUESTIONS;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.shared.models.DecisionModels;
import io.littlehorse.shared.models.ModelResponse;
import io.littlehorse.shared.orders.Order;
import io.littlehorse.shared.orders.OrderStore;
import java.util.Map;

/**
 * Decision Workers for support tickets: read-only access to orders and no ability to act. Each returns a
 * flat JSON object; the WfSpec applies the confidence and manipulation gates and takes the action.
 */
@LHTask
public class SupportTicketDecisionWorker {

    public static final String TRIAGE_TICKET = "triage-ticket-";
    public static final String PICK_WORKFLOW = "pick-workflow-";

    private final DecisionModels models;
    private final OrderStore orders;

    public SupportTicketDecisionWorker(DecisionModels models, OrderStore orders) {
        this.models = models;
        this.orders = orders;
    }

    @LHTaskMethod(TRIAGE_TICKET + JEV)
    public Map<String, Object> triageJev(String emailBody, String orderId, WorkerContext ctx) throws Exception {
        return triage(JEV, emailBody, orderId, ctx);
    }

    @LHTaskMethod(TRIAGE_TICKET + OPENAI)
    public Map<String, Object> triageOpenAi(String emailBody, String orderId, WorkerContext ctx) throws Exception {
        return triage(OPENAI, emailBody, orderId, ctx);
    }

    /** Workflow dispatch: the choice is the name of the child WfSpec to run. */
    @LHTaskMethod(PICK_WORKFLOW + JEV)
    public Map<String, Object> pickWorkflowJev(String emailBody, String orderId, WorkerContext ctx) throws Exception {
        ModelResponse r = models.ask(JEV, ctx, state(emailBody, orderId), DISPATCH_QUESTIONS);
        ModelResponse.Answer workflow = r.get("workflow");
        return r.toResult("workflow", workflow.choice(), "confidence", workflow.confidence(),
                "probabilities", workflow.probabilities(), "manipulation", r.get("manipulation").noul());
    }

    private Map<String, Object> triage(String engine, String emailBody, String orderId, WorkerContext ctx)
            throws Exception {
        ModelResponse r = models.ask(engine, ctx, state(emailBody, orderId), TRIAGE_QUESTIONS);
        ModelResponse.Answer action = r.get("action");
        return r.toResult("action", action.choice(), "confidence", action.confidence(),
                "probabilities", action.probabilities(), "manipulation", r.get("manipulation").noul());
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
