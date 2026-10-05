package io.littlehorse.examples.screening.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "verify")
@Path("/mock/verify")
public interface VerificationClient {

    @GET
    @Path("/employment/{applicationId}")
    Map<String, Object> employment(@PathParam("applicationId") String applicationId);
}
