package io.littlehorse.examples.screening.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "ats")
@Path("/mock/ats")
public interface AtsClient {

    @GET
    @Path("/applications/{applicationId}")
    Map<String, Object> application(@PathParam("applicationId") String applicationId);

    @GET
    @Path("/roles/{track}")
    Map<String, Object> role(@PathParam("track") String track);
}
