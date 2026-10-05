package io.littlehorse.examples.support_ticket.tasks;

import io.littlehorse.shared.orders.Order;
import java.util.Map;

/** Triage outcomes; the descriptions the model sees live in SupportTicketPolicy. */
public enum TriageDecision {
    ESCALATE_TO_TEAM,
    SEND_ORDER_INFO,
    CANCEL_ORDER;

    /** The context every decision model sees: the untrusted email plus read-only order data. */
    public static Map<String, Object> state(String emailBody, Order order) {
        return Map.of(
                "customer_email", emailBody,
                "order", Map.of(
                        "status", order.status(),
                        "items", order.items(),
                        "total", order.total().toPlainString()));
    }
}
