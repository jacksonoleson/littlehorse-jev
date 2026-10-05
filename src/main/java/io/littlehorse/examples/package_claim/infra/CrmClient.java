package io.littlehorse.examples.package_claim.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "crm")
@Path("/mock/crm")
public interface CrmClient {

    @GET
    @Path("/customers/{customerId}/claims")
    Map<String, Object> claims(@PathParam("customerId") String customerId);
}
