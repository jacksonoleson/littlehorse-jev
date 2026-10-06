package io.littlehorse.examples.package_claim.structs;

import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decision 4: refund, reship, deny, or human review. */
@LHStructDef("claim-resolution")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResolution {
    private String decision;
    private double confidence;
    private String model;
}
