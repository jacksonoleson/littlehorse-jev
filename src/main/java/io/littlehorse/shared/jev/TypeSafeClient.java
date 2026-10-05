package io.littlehorse.shared.jev;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "typesafe")
@ClientHeaderParam(name = "Authorization", value = "Bearer ${typesafe.api-key}")
@Path("/v1")
public interface TypeSafeClient {

    @POST
    @Path("/systemone")
    SystemOne.Response systemOne(SystemOne.Request request);
}
