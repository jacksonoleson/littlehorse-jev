package io.littlehorse.examples.package_claim.structs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decision 2: what the carrier's evidence shows. Each field is a Noul (probability of yes). */
@LHStructDef("tracking-evidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
// Passed back to Jev in decision 4: sorted snake_case keys, matching the question ids that produced each field.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder(alphabetic = true)
public class TrackingEvidence {
    private double delivered;
    private double proofAtAddress;
    private double wrongLocation;
    private double contradictsCustomer;
    private String model;
}
