package io.littlehorse.common.helpdesk;

import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.ExternalEventDefId;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.PutExternalEventRequest;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logging.Logger;

/**
 * Helpdesk, served by this app. An agent resolves every case after a short delay and posts
 * the case's callback ExternalEvent, standing in for a real helpdesk's "resolved" webhook.
 */
@Path("/helpdesk")
public class HelpdeskResource {

    private static final Logger LOG = Logger.getLogger(HelpdeskResource.class);
    private static final Executor AGENT = CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS);

    private final LittleHorseBlockingStub lh;
    private final AtomicInteger nextCase = new AtomicInteger(100);

    public HelpdeskResource(LittleHorseBlockingStub lh) {
        this.lh = lh;
    }

    @POST
    @Path("/cases")
    public Map<String, Object> openCase(HelpdeskClient.NewCase c) {
        String caseId = "CASE-" + nextCase.incrementAndGet();
        LOG.infof("Helpdesk %s opened | %s | %s", caseId, c.subject(), c.notes());
        CompletableFuture.runAsync(() -> resolve(caseId, c), AGENT)
                .exceptionally(e -> {
                    LOG.errorf(e, "Helpdesk %s couldn't call back %s", caseId, c.wfRunId());
                    return null;
                });
        return Map.of("case_id", caseId, "status", "OPEN");
    }

    private void resolve(String caseId, HelpdeskClient.NewCase c) {
        lh.putExternalEvent(PutExternalEventRequest.newBuilder()
                .setWfRunId(LHLibUtil.wfRunIdFromString(c.wfRunId()))
                .setExternalEventDefId(ExternalEventDefId.newBuilder().setName(c.callbackEvent()))
                .setContent(LHLibUtil.objToVarVal(
                        Map.of("case_id", caseId, "resolution", "RESOLVED", "resolved_by", "support-agent")))
                .build());
        LOG.infof("Helpdesk %s resolved by support-agent", caseId);
    }
}
