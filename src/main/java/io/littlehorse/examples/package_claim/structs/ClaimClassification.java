package io.littlehorse.examples.package_claim.structs;

import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decision 1: what the customer is reporting. */
@LHStructDef("claim-classification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimClassification {
    private String type;
    private double confidence;
    private String model;
}
