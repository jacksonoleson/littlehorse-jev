package io.littlehorse.examples.package_claim.infra;

import java.util.List;
import java.util.Map;

/** CRM: each customer's account age and past claims. */
public final class Crm {

    private Crm() {}

    private static final Map<String, Map<String, Object>> CUSTOMERS = Map.of(
            "alice", customer("alice", 1460, 32, List.of()),
            "carol", customer("carol", 700, 10, List.of()),
            "dave", customer("dave", 60, 6, List.of(
                    claim("ORD-3901", "2026-09-12", "missing package", "refunded"),
                    claim("ORD-3822", "2026-08-03", "missing package", "refunded"),
                    claim("ORD-3790", "2026-07-19", "missing package", "reshipped"),
                    claim("ORD-3701", "2026-06-02", "missing package", "refunded"))),
            "erin", customer("erin", 900, 18, List.of()),
            "frank", customer("frank", 400, 7, List.of()),
            "gina", customer("gina", 1200, 25, List.of(claim("ORD-6102", "2025-03-14", "damaged item", "replaced"))));

    public static Map<String, Object> claims(String customerId) {
        return CUSTOMERS.getOrDefault(customerId, customer(customerId, 0, 0, List.of()));
    }

    private static Map<String, Object> customer(
            String id, int accountAgeDays, int lifetimeOrders, List<Map<String, String>> priorClaims) {
        return Map.of(
                "customer_id", id,
                "account_age_days", accountAgeDays,
                "lifetime_orders", lifetimeOrders,
                "claims_last_12_months", priorClaims.stream().filter(c -> c.get("date").startsWith("2026")).count(),
                "prior_claims", priorClaims);
    }

    private static Map<String, String> claim(String orderId, String date, String type, String outcome) {
        return Map.of("order_id", orderId, "date", date, "type", type, "outcome", outcome);
    }
}
