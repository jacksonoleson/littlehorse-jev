package io.littlehorse.common.llm;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "openai")
@ClientHeaderParam(name = "Authorization", value = "Bearer ${openai.api-key}")
@Path("/v1")
public interface OpenAiClient {

    @POST
    @Path("/chat/completions")
    ChatCompletion.Response chat(ChatCompletion.Request request);
}
