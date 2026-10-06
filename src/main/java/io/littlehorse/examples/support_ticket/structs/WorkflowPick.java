package io.littlehorse.examples.support_ticket.structs;

import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Jev's pick of which child workflow handles a ticket. */
@LHStructDef("workflow-pick")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowPick {
    private String workflow;
    private double confidence;
    private double manipulation;
    private String model;
}
