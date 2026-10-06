package io.littlehorse.examples.screening.workflow;

import io.littlehorse.quarkus.task.LHUserTaskForm;
import io.littlehorse.sdk.usertask.annotations.UserTaskField;

@LHUserTaskForm(RecruiterReviewForm.RECRUITER_REVIEW)
public class RecruiterReviewForm {

    public static final String RECRUITER_REVIEW = "recruiter-review";

    @UserTaskField(displayName = "Decision", description = "Advance, hold, or decline?")
    public String decision;

    @UserTaskField(displayName = "Rationale", description = "Why this decision?")
    public String rationale;
}
