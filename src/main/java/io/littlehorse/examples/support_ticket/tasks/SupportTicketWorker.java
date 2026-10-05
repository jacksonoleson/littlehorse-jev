package io.littlehorse.examples.support_ticket.tasks;

import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.common.helpdesk.HelpdeskClient;
import io.littlehorse.common.orders.Order;
import io.littlehorse.common.orders.OrderStore;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Deterministic tasks that validate input and perform privileged actions. No AI involved here. */
@LHTask
public class SupportTicketWorker {

    public static final String EXTRACT_ORDER_ID = "extract-order-id";
    public static final String VALIDATE_ORDER_AND_USER = "validate-order-and-user";
    public static final String PROCESS_REFUND = "process-refund";
    public static final String CANCEL_ORDER = "cancel-order";
    public static final String SEND_EMAIL = "send-email";
    public static final String SEND_ORDER_INFO = "send-order-info";
    public static final String CREATE_RETURN_LABEL = "create-return-label";
    public static final String OPEN_SUPPORT_CASE = "open-support-case";
    /** ExternalEventDef the helpdesk posts when an agent resolves the case. */
    public static final String SUPPORT_CASE_RESOLVED = "support-case-resolved";

    private static final Logger LOG = Logger.getLogger(SupportTicketWorker.class);
    private static final Pattern ORDER_ID = Pattern.compile("\\bORD-\\d+\\b");

    private final OrderStore orders;
    private final HelpdeskClient helpdesk;

    public SupportTicketWorker(OrderStore orders, @RestClient HelpdeskClient helpdesk) {
        this.orders = orders;
        this.helpdesk = helpdesk;
    }

    @LHTaskMethod(EXTRACT_ORDER_ID)
    public String extractOrderId(String emailBody) {
        Matcher m = ORDER_ID.matcher(emailBody);
        return m.find() ? m.group() : "";
    }

    @LHTaskMethod(VALIDATE_ORDER_AND_USER)
    public boolean validateOrderAndUser(String userId, String orderId) {
        return orders.findById(orderId).map(o -> o.userId().equals(userId)).orElse(false);
    }

    @LHTaskMethod(PROCESS_REFUND)
    public String processRefund(String userId, String orderId) {
        Order order = requireOwned(userId, orderId);
        LOG.infof("Refunded %s to %s for %s", order.total(), userId, orderId);
        return "Refunded " + order.total();
    }

    @LHTaskMethod(CANCEL_ORDER)
    public String cancelOrder(String userId, String orderId) {
        requireOwned(userId, orderId);
        orders.updateStatus(orderId, "CANCELLED");
        LOG.infof("Cancelled %s for %s", orderId, userId);
        return "CANCELLED";
    }

    @LHTaskMethod(CREATE_RETURN_LABEL)
    public String createReturnLabel(String userId, String orderId) {
        requireOwned(userId, orderId);
        String label = "RMA-" + orderId.substring(orderId.indexOf('-') + 1);
        LOG.infof("Created return label %s for %s", label, orderId);
        return label;
    }

    @LHTaskMethod(SEND_EMAIL)
    public void sendEmail(String userId, String subject, String body) {
        LOG.infof("Email to %s | %s | %s", userId, subject, body);
    }

    @LHTaskMethod(SEND_ORDER_INFO)
    public void sendOrderInfo(String orderId, String userId) {
        Order order = requireOwned(userId, orderId);
        sendEmail(userId, "Your order " + orderId,
                "Status: %s, items: %s, total: %s".formatted(order.status(), order.items(), order.total()));
    }

    @LHTaskMethod(OPEN_SUPPORT_CASE)
    public Map<String, Object> openSupportCase(String notes, WorkerContext ctx) {
        return helpdesk.openCase(new HelpdeskClient.NewCase(
                LHLibUtil.wfRunIdToString(ctx.getWfRunId()), SUPPORT_CASE_RESOLVED, "Support ticket", notes));
    }

    // Defense in depth: privileged tasks re-check ownership even though the WfSpec already did.
    private Order requireOwned(String userId, String orderId) {
        return orders.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not belong to " + userId));
    }
}
