package io.littlehorse.examples.screening.infra;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "recruiting")
@Path("/mock/recruiting")
public interface RecruitingToolClient {

    /** Identifies one UserTaskRun: its WfRun plus the task's guid. */
    record ReviewRequest(String wfRunId, String userTaskGuid) {}

    @POST
    @Path("/reviews")
    Map<String, Object> requestReview(ReviewRequest request);
}
