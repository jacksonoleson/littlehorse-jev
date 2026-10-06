package io.littlehorse.examples.package_claim.structs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decision 3: how likely the customer is to be abusing claims. */
@LHStructDef("customer-risk")
@Data
@NoArgsConstructor
@AllArgsConstructor
// Passed back to Jev in decision 4: sorted snake_case keys, matching the question ids that produced each field.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder(alphabetic = true)
public class CustomerRisk {
    /** LOW, MEDIUM or HIGH: a label, not a score index. */
    private String abuseRisk;
    private double confidence;
    private double pressureTactics;
    private String model;
}
