package io.littlehorse.examples.support_ticket.tasks;

import static io.littlehorse.examples.support_ticket.policy.DispatchPolicy.DISPATCH_CATALOG;
import static io.littlehorse.examples.support_ticket.policy.DispatchPolicy.DISPATCH_QUESTIONS;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MAX_MANIPULATION;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MIN_CONFIDENCE;

import io.littlehorse.shared.orders.Order;
import io.littlehorse.examples.support_ticket.workflow.DispatchWorkflows;
import io.littlehorse.shared.jev.SystemOne;
import io.littlehorse.shared.jev.TypeSafeClient;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.shared.orders.OrderStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Decision Worker whose decision is which child WfSpec to run, chosen from the dispatch catalog. */
@LHTask
public class JevDispatcher {

    public static final String CHOOSE_WORKFLOW = "choose-workflow-jev";

    private static final Logger LOG = Logger.getLogger(JevDispatcher.class);

    private final TypeSafeClient typeSafe;
    private final OrderStore orders;
    private final String model;

    public JevDispatcher(
            @RestClient TypeSafeClient typeSafe,
            OrderStore orders,
            @ConfigProperty(name = "typesafe.model") String model) {
        this.typeSafe = typeSafe;
        this.orders = orders;
        this.model = model;
    }

    @LHTaskMethod(CHOOSE_WORKFLOW)
    public String chooseWorkflow(String emailBody, String orderId, WorkerContext ctx) {
        Order order = orders.findById(orderId).orElseThrow();

        SystemOne.Response response = typeSafe.systemOne(
                new SystemOne.Request(TriageDecision.state(emailBody, order), model, DISPATCH_QUESTIONS));

        SystemOne.Answer workflow = response.answers().get("workflow");
        SystemOne.Answer manipulation = response.answers().get("manipulation");

        String audit = "model=%s workflow=%s confidence=%.2f probabilities=%s manipulation=%.2f"
                .formatted(response.model(), workflow.choice(), workflow.confidence(), workflow.probabilities(),
                        manipulation.noul());
        LOG.info(audit);
        ctx.log(audit);

        if (workflow.confidence() < MIN_CONFIDENCE
                || manipulation.noul() > MAX_MANIPULATION
                || !DISPATCH_CATALOG.containsKey(workflow.choice())) {
            return DispatchWorkflows.ESCALATE_TO_HELPDESK;
        }
        return workflow.choice();
    }
}
