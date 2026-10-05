package io.littlehorse.examples.support_ticket.tasks;

import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MAX_MANIPULATION;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.MIN_CONFIDENCE;
import static io.littlehorse.examples.support_ticket.policy.SupportTicketPolicy.TRIAGE_QUESTIONS;

import io.littlehorse.shared.jev.SystemOne;
import io.littlehorse.shared.jev.TypeSafeClient;
import io.littlehorse.shared.orders.Order;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.shared.orders.OrderStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * Decision Worker: asks Jev to pick a {@link TriageDecision}. It has read-only access to orders
 * and no ability to take action; the WfSpec acts on the decision it returns.
 */
@LHTask
public class JevDecisionWorker {

    public static final String TRIAGE_TASK = "triage-support-ticket-jev";

    private static final Logger LOG = Logger.getLogger(JevDecisionWorker.class);

    private final TypeSafeClient typeSafe;
    private final OrderStore orders;
    private final String model;

    public JevDecisionWorker(
            @RestClient TypeSafeClient typeSafe,
            OrderStore orders,
            @ConfigProperty(name = "typesafe.model") String model) {
        this.typeSafe = typeSafe;
        this.orders = orders;
        this.model = model;
    }

    @LHTaskMethod(TRIAGE_TASK)
    public String triageTicket(String emailBody, String orderId, WorkerContext ctx) {
        Order order = orders.findById(orderId).orElseThrow();

        SystemOne.Response response = typeSafe.systemOne(
                new SystemOne.Request(TriageDecision.state(emailBody, order), model, TRIAGE_QUESTIONS));

        SystemOne.Answer action = response.answers().get("action");
        SystemOne.Answer manipulation = response.answers().get("manipulation");

        String audit = "model=%s choice=%s confidence=%.2f probabilities=%s manipulation=%.2f"
                .formatted(response.model(), action.choice(), action.confidence(), action.probabilities(),
                        manipulation.noul());
        LOG.info(audit);
        ctx.log(audit);

        // Deterministic guardrails: when in doubt, a human decides.
        if (action.confidence() < MIN_CONFIDENCE || manipulation.noul() > MAX_MANIPULATION) {
            return TriageDecision.ESCALATE_TO_TEAM.name();
        }
        try {
            return TriageDecision.valueOf(action.choice()).name();
        } catch (IllegalArgumentException e) {
            return TriageDecision.ESCALATE_TO_TEAM.name();
        }
    }
}
