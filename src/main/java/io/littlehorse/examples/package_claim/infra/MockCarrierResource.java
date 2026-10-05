package io.littlehorse.examples.package_claim.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;
import java.util.Map;

/** Fake parcel-carrier tracking API, served by this app so tasks make a real HTTP call. */
@Path("/mock/carrier")
public class MockCarrierResource {

    private static final Map<String, Map<String, Object>> TRACKING = Map.of(
            "TRK-1001", delivered("TRK-1001", "12 Birch Ln, Portland OR", 8,
                    Map.of("photo", "Box on the front porch beside a blue door, house number 12 visible"),
                    "2026-10-02T14:12:00Z"),
            "TRK-3001", delivered("TRK-3001", "Parcel locker bank, 14 Elm St, Denver CO", 2400,
                    Map.of(), "2026-10-01T18:40:00Z"),
            "TRK-4001", delivered("TRK-4001", "5 Maple Ct, Tampa FL", 5,
                    Map.of("signature", "D. MILLER", "photo", "Package handed to resident at the front door"),
                    "2026-09-30T11:05:00Z"),
            "TRK-5001", Map.of(
                    "tracking_number", "TRK-5001",
                    "carrier", "Acme Parcel",
                    "status", "IN_TRANSIT",
                    "estimated_delivery", "2026-10-06",
                    "events", List.of(
                            event("2026-09-29T09:00:00Z", "Salt Lake City UT", "Departed facility"),
                            event("2026-10-01T07:30:00Z", "Salt Lake City UT", "Delayed: severe weather on route"))),
            "TRK-6001", delivered("TRK-6001", "19 Elm St, Dayton OH", 6,
                    Map.of("photo", "Box at the front door"), "2026-10-03T16:20:00Z"),
            "TRK-7001", delivered("TRK-7001", "44 Spruce Way, Reno NV", 15,
                    Map.of("photo", "Small padded envelope in the mailbox"), "2026-10-02T12:00:00Z"));

    @GET
    @Path("/tracking/{trackingNumber}")
    public Map<String, Object> tracking(@PathParam("trackingNumber") String trackingNumber) {
        return TRACKING.getOrDefault(trackingNumber, Map.of(
                "tracking_number", trackingNumber, "status", "LABEL_CREATED", "events", List.of()));
    }

    private static Map<String, Object> delivered(
            String trackingNumber, String location, int metersFromAddress, Map<String, String> proof, String at) {
        return Map.of(
                "tracking_number", trackingNumber,
                "carrier", "Acme Parcel",
                "status", "DELIVERED",
                "events", List.of(event(at, location, "Delivered")),
                "delivery", Map.of(
                        "delivered_at", at,
                        "location", location,
                        "gps_distance_from_address_m", metersFromAddress,
                        "proof", proof));
    }

    private static Map<String, String> event(String time, String location, String description) {
        return Map.of("time", time, "location", location, "description", description);
    }
}
