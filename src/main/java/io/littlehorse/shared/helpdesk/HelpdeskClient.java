package io.littlehorse.shared.helpdesk;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "helpdesk")
@Path("/helpdesk")
public interface HelpdeskClient {

    /** {@code callbackEvent} is the ExternalEventDef the helpdesk posts to {@code wfRunId} once resolved. */
    record NewCase(String wfRunId, String callbackEvent, String subject, String notes) {}

    @POST
    @Path("/cases")
    Map<String, Object> openCase(NewCase newCase);
}
