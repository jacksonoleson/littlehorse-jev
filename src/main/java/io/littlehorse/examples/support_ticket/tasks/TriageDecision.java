package io.littlehorse.examples.support_ticket.tasks;

/** Triage outcomes; the descriptions the model sees live in SupportTicketPolicy. */
public enum TriageDecision {
    ESCALATE_TO_TEAM,
    SEND_ORDER_INFO,
    CANCEL_ORDER
}
