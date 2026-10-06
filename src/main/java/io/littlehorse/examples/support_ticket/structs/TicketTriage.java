package io.littlehorse.examples.support_ticket.structs;

import io.littlehorse.sdk.worker.LHStructDef;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Jev's triage of a support ticket. */
@LHStructDef("ticket-triage")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketTriage {
    private String action;
    private double confidence;
    private double manipulation;
    private String model;
}
