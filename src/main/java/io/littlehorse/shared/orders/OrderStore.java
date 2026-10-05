package io.littlehorse.shared.orders;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stand-in for a real order/payments backend. */
@ApplicationScoped
public class OrderStore {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public OrderStore() {
        save(order("ORD-1001", "alice", "SHIPPED", "129.99", "12 Birch Ln, Portland OR", "TRK-1001", "Mechanical keyboard"));
        save(order("ORD-1002", "alice", "PROCESSING", "54.50", "12 Birch Ln, Portland OR", null, "USB-C hub", "HDMI cable"));
        save(order("ORD-2001", "bob", "PROCESSING", "399.00", "7 Pine St, Austin TX", null, "4K monitor"));
        // Package-claim scenarios; tracking lives in the mock carrier service.
        save(order("ORD-3001", "carol", "SHIPPED", "899.00", "88 Oak Ave, Denver CO", "TRK-3001", "Laptop"));
        save(order("ORD-4001", "dave", "SHIPPED", "349.00", "5 Maple Ct, Tampa FL", "TRK-4001", "Noise-cancelling headphones"));
        save(order("ORD-5001", "erin", "SHIPPED", "85.00", "301 Cedar Rd, Boise ID", "TRK-5001", "Running shoes"));
        save(order("ORD-6001", "frank", "SHIPPED", "29.00", "19 Elm St, Dayton OH", "TRK-6001", "Ceramic mug set"));
        save(order("ORD-7001", "gina", "SHIPPED", "19.00", "44 Spruce Way, Reno NV", "TRK-7001", "Phone case"));
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public Order updateStatus(String orderId, String status) {
        return orders.computeIfPresent(orderId, (id, order) -> order.withStatus(status));
    }

    private void save(Order order) {
        orders.put(order.orderId(), order);
    }

    private static Order order(
            String id, String user, String status, String total, String address, String tracking, String... items) {
        return new Order(id, user, status, List.of(items), new BigDecimal(total), address, tracking);
    }
}
