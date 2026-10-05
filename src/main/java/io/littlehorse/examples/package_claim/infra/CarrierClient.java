package io.littlehorse.examples.package_claim.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "carrier")
@Path("/mock/carrier")
public interface CarrierClient {

    @GET
    @Path("/tracking/{trackingNumber}")
    Map<String, Object> tracking(@PathParam("trackingNumber") String trackingNumber);
}
