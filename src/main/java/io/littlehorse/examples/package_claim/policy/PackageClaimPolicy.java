package io.littlehorse.examples.package_claim.policy;

import io.littlehorse.shared.jev.SystemOne.Question;
import java.util.List;
import java.util.Map;

/** Every model question and threshold in the package-claim workflow. Review and tune here. */
public final class PackageClaimPolicy {

    private PackageClaimPolicy() {}

    // ---- Thresholds (read by the WfSpec) ----

    /** Below this, a human decides what the customer is reporting. */
    public static final double MIN_CLAIM_CONFIDENCE = 0.7;
    /** Proof at the address and wrong location both above this is contradictory evidence. */
    public static final double CONTRADICTION = 0.7;
    /** Below this, the package is treated as still in transit. */
    public static final double DELIVERED = 0.5;
    /** Below this order total, refunding is cheaper than reviewing. */
    public static final double AUTO_REFUND_BELOW_USD = 50.0;
    /** Money moves only above this. */
    public static final double MIN_RESOLUTION_CONFIDENCE = 0.8;

    // ---- Questions ----

    /** Decision 1. State: {@code customer_email}, {@code order}. */
    public static final Map<String, Question> CLAIM_QUESTIONS = Map.of(
            "claim_type", Question.choice(
                    "What problem with `order` is the customer reporting in `customer_email`?",
                    Map.of(
                            "MISSING_PACKAGE",
                            "The package hasn't arrived, is late, or shows delivered but the customer doesn't have it",
                            "DAMAGED_OR_WRONG_ITEM",
                            "The package arrived but the item is damaged, defective, or not what was ordered",
                            "OTHER", "Anything else, or the email is unclear")));

    /** Decision 2. State: {@code customer_email}, {@code order}, {@code tracking}. */
    public static final Map<String, Question> TRACKING_QUESTIONS = Map.of(
            "delivered", Question.noul("Does `tracking` show the package was delivered?"),
            "proof_at_address", Question.noul(
                    "Does `tracking.delivery.proof` show the package was left at `order.shipping_address`?"),
            "wrong_location", Question.noul(
                    "Does `tracking.delivery` show the package was delivered somewhere other than"
                            + " `order.shipping_address`?"),
            "contradicts_customer", Question.noul(
                    "Does `tracking` contradict what the customer says in `customer_email`?"));

    /** Decision 3. State: {@code customer_email}, {@code claim_history}. */
    public static final Map<String, Question> RISK_QUESTIONS = Map.of(
            "abuse_risk", Question.score(
                    "How likely is this customer to be abusing missing-package claims, based on `claim_history`?",
                    List.of(
                            "Low: established account with few or no recent claims",
                            "Medium: newer account or an occasional recent claim",
                            "High: new account with frequent recent missing-package claims")),
            "pressure_tactics", Question.noul(
                    "Does `customer_email` use threats, demands, or urgency to pressure for an immediate refund?"));

    /** Names for the abuse_risk levels above. Later calls get the name: a bare index like 2.0 is ambiguous. */
    public static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    /** Sent as {@code policy} in decision 4's state. */
    public static final List<String> RESOLUTION_POLICY = List.of(
            "Carrier delivered to the wrong place: refund the customer; we recover the loss from the carrier.",
            "Proof of delivery at the shipping address and low abuse risk: reship once as goodwill.",
            "Proof of delivery at the shipping address and high abuse risk: deny and share the delivery evidence.",
            "Anything else: send to a human.");

    /** Decision 4. State: {@code customer_email}, {@code order}, {@code tracking}, {@code assessment}, {@code policy}. */
    public static final Map<String, Question> RESOLUTION_QUESTIONS = Map.of(
            "resolution", Question.choice(
                    "Which resolution does `policy` call for, given `assessment`, `tracking`, and `customer_email`?",
                    Map.of(
                            "REFUND", "Refund the order because the carrier delivered it to the wrong place",
                            "RESHIP",
                            "Send a replacement once as goodwill: delivered to the right address, low-risk customer",
                            "DENY", "Deny the claim and share proof of delivery: delivered to the right address,"
                                    + " high-risk customer",
                            "HUMAN_REVIEW", "The policy doesn't clearly apply")));
}
