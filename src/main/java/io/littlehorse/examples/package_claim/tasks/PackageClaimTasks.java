package io.littlehorse.examples.package_claim.tasks;

import io.littlehorse.examples.package_claim.infra.CarrierClient;
import io.littlehorse.examples.package_claim.infra.Crm;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.worker.LHTaskMethod;
import io.littlehorse.sdk.worker.WorkerContext;
import io.littlehorse.shared.helpdesk.HelpdeskClient;
import io.littlehorse.shared.orders.Order;
import io.littlehorse.shared.orders.OrderStore;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Deterministic steps of the package-claim workflow: lookups, outside-service calls, and actions. */
@LHTask
public class PackageClaimTasks {

    public static final String FIND_CLAIMED_ORDER = "find-claimed-order";
    public static final String FETCH_TRACKING = "fetch-tracking";
    public static final String FETCH_CLAIM_HISTORY = "fetch-claim-history";
    public static final String NOTIFY_IN_TRANSIT = "notify-in-transit";
    public static final String REFUND_CLAIM = "refund-claim";
    public static final String RESHIP_ORDER = "reship-order";
    public static final String SEND_RETURN_LABEL = "send-return-label";
    public static final String SEND_DELIVERY_EVIDENCE = "send-delivery-evidence";
    public static final String OPEN_CLAIM_REVIEW = "open-claim-review";
    /** ExternalEventDef the helpdesk posts when an agent finishes the review. */
    public static final String CLAIM_REVIEW_COMPLETED = "claim-review-completed";

    private static final Logger LOG = Logger.getLogger(PackageClaimTasks.class);
    private static final Pattern ORDER_ID = Pattern.compile("\\bORD-\\d+\\b");

    private final OrderStore orders;
    private final CarrierClient carrier;
    private final HelpdeskClient helpdesk;

    public PackageClaimTasks(
            OrderStore orders, @RestClient CarrierClient carrier, @RestClient HelpdeskClient helpdesk) {
        this.orders = orders;
        this.carrier = carrier;
        this.helpdesk = helpdesk;
    }

    @LHTaskMethod(OPEN_CLAIM_REVIEW)
    public Map<String, Object> openClaimReview(String reason, WorkerContext ctx) {
        return helpdesk.openCase(new HelpdeskClient.NewCase(
                LHLibUtil.wfRunIdToString(ctx.getWfRunId()), CLAIM_REVIEW_COMPLETED, "Package claim", reason));
    }

    /** The order named in the email, only if it belongs to the sender; otherwise {@code order_id} is empty. */
    @LHTaskMethod(FIND_CLAIMED_ORDER)
    public Map<String, Object> findClaimedOrder(String userId, String emailBody) {
        Matcher m = ORDER_ID.matcher(emailBody);
        Optional<Order> order = m.find() ? owned(userId, m.group()) : Optional.empty();
        return order.<Map<String, Object>>map(o -> Map.of(
                        "order_id", o.orderId(),
                        "status", o.status(),
                        "items", o.items(),
                        "total", o.total().doubleValue(),
                        "shipping_address", o.shippingAddress()))
                .orElse(Map.of("order_id", ""));
    }

    @LHTaskMethod(FETCH_TRACKING)
    public Map<String, Object> fetchTracking(String orderId) {
        String trackingNumber = orders.findById(orderId).orElseThrow().trackingNumber();
        return trackingNumber == null
                ? Map.of("status", "NOT_SHIPPED")
                : carrier.tracking(trackingNumber);
    }

    @LHTaskMethod(FETCH_CLAIM_HISTORY)
    public Map<String, Object> fetchClaimHistory(String userId) {
        return Crm.claims(userId);
    }

    @LHTaskMethod(NOTIFY_IN_TRANSIT)
    public void notifyInTransit(String userId, Map<String, Object> tracking) {
        LOG.infof("Email to %s | Your package is on its way | Carrier now expects delivery on %s.",
                userId, tracking.getOrDefault("estimated_delivery", "a later date"));
    }

    @LHTaskMethod(REFUND_CLAIM)
    public String refundClaim(String userId, String orderId) {
        Order o = owned(userId, orderId).orElseThrow();
        LOG.infof("Refunded %s for %s | Email to %s | We've refunded your missing package.",
                o.total(), orderId, userId);
        return "Refunded " + o.total();
    }

    @LHTaskMethod(RESHIP_ORDER)
    public String reshipOrder(String userId, String orderId) {
        Order o = owned(userId, orderId).orElseThrow();
        String reshipId = "RS-" + o.orderId().substring(o.orderId().indexOf('-') + 1);
        LOG.infof("Reshipped %s as %s to %s | Email to %s | A replacement is on its way.",
                o.items(), reshipId, o.shippingAddress(), userId);
        return reshipId;
    }

    @LHTaskMethod(SEND_RETURN_LABEL)
    public String sendReturnLabel(String userId, String orderId) {
        owned(userId, orderId).orElseThrow();
        String label = "RMA-" + orderId.substring(orderId.indexOf('-') + 1);
        LOG.infof("Email to %s | Your return label | Use return label %s to send back order %s.",
                userId, label, orderId);
        return label;
    }

    @LHTaskMethod(SEND_DELIVERY_EVIDENCE)
    public void sendDeliveryEvidence(String userId, Map<String, Object> tracking) {
        LOG.infof("Email to %s | About your missing-package claim | The carrier confirmed delivery: %s",
                userId, tracking.get("delivery"));
    }

    // Privileged tasks re-check ownership even though the WfSpec already did.
    private Optional<Order> owned(String userId, String orderId) {
        return orders.findById(orderId).filter(o -> o.userId().equals(userId));
    }
}
