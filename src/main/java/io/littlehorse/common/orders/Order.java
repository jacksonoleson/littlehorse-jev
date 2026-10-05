package io.littlehorse.common.orders;

import java.math.BigDecimal;
import java.util.List;

public record Order(
        String orderId,
        String userId,
        String status,
        List<String> items,
        BigDecimal total,
        String shippingAddress,
        String trackingNumber) {

    public Order withStatus(String newStatus) {
        return new Order(orderId, userId, newStatus, items, total, shippingAddress, trackingNumber);
    }
}
