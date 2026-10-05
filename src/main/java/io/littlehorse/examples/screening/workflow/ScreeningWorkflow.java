package io.littlehorse.examples.screening.workflow;

import static io.littlehorse.examples.screening.policy.ScreeningPolicy.*;
import static io.littlehorse.examples.screening.tasks.RecruiterDecisionWorker.COMPLETE_RECRUITER_REVIEW;
import static io.littlehorse.examples.screening.tasks.ScreeningDecisionWorker.*;
import static io.littlehorse.examples.screening.tasks.ScreeningWorker.*;
import static io.littlehorse.examples.screening.workflow.RecruiterReviewForm.RECRUITER_REVIEW;
import static io.littlehorse.shared.models.DecisionModels.JEV;
import static io.littlehorse.shared.models.DecisionModels.OPENAI;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.sdk.wfsdk.TaskNodeOutput;
import io.littlehorse.sdk.wfsdk.UserTaskOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Blind resume screening. Up to four model calls, each asking many questions in parallel; the
 * WfSpec branches on their typed answers and feeds them into later calls. No screening call declines a
 * candidate: every decline goes through the recruiter-review user task, which a Jev "recruiter" completes.
 */
@ApplicationScoped
public class ScreeningWorkflow {

    public static final String JEV_WF = "screen-candidate-jev";
    public static final String OPENAI_WF = "screen-candidate-openai";

    @LHWorkflow(JEV_WF)
    public void jev(WorkflowThread wf) {
        define(wf, JEV);
    }

    @LHWorkflow(OPENAI_WF)
    public void openAi(WorkflowThread wf) {
        define(wf, OPENAI);
    }

    private void define(WorkflowThread wf, String engine) {
        WfRunVariable applicationId = wf.declareStr("application-id").required().searchable();
        WfRunVariable outcome = wf.declareStr("outcome").searchable();
        WfRunVariable application = wf.declareJsonObj("application");
        WfRunVariable triage = wf.declareJsonObj("triage");
        WfRunVariable role = wf.declareJsonObj("role");
        WfRunVariable fit = wf.declareJsonObj("fit");
        WfRunVariable requirements = wf.declareJsonObj("requirements");
        WfRunVariable verification = wf.declareJsonObj("verification");
        WfRunVariable history = wf.declareJsonObj("history-check");
        WfRunVariable recommendation = wf.declareJsonObj("recommendation");
        WfRunVariable review = wf.declareJsonObj("recruiter-review");

        outcome.assign("SCREENING");
        application.assign(wf.execute(FETCH_APPLICATION, applicationId));

        // Call 1 (intent routing + speculative fan-out): track, seniority, spam, remote, 5 skill scores.
        triage.assign(decide(wf.execute(TRIAGE_APPLICATION + engine, application)));

        wf.doIf(triage.jsonPath("$.spam").isGreaterThan(SPAM), spam -> {
                    outcome.assign("CLOSED_SPAM");
                    spam.execute(CLOSE_APPLICATION, applicationId, "Spam or empty application");
                })
                .doElseIf(triage.jsonPath("$.track_confidence").isLessThan(MIN_TRACK_CONFIDENCE),
                        unclearRole -> recruiter(unclearRole, outcome, review, "Unclear which role fits"))
                .doElseIf(triage.jsonPath("$.track").isEqualTo("NOT_A_FIT"),
                        noRoleFits -> recruiter(noRoleFits, outcome, review, "No open role fits; confirm decline"))
                .doElse(roleFits -> {
                    // The role (and its requirements and weights) depends on the track the model chose.
                    role.assign(roleFits.execute(FETCH_ROLE, triage.jsonPath("$.track")));
                    fit.assign(roleFits.execute(COMPUTE_FIT_SCORE, triage, role));

                    // Call 2: one Noul per requirement of the chosen role.
                    requirements.assign(decide(roleFits.execute(CHECK_REQUIREMENTS + engine, application, role)));

                    roleFits.doIf(fit.jsonPath("$.remote_conflict").isEqualTo(true),
                                    remoteConflict -> recruiter(remoteConflict, outcome, review,
                                            "Wants fully remote; role is onsite"))
                            .doElseIf(fit.jsonPath("$.seniority_ok").isEqualTo(false),
                                    tooJunior -> recruiter(tooJunior, outcome, review,
                                            "Below the role's seniority bar; confirm decline"))
                            .doElseIf(requirements.jsonPath("$.min_must_have").isLessThan(MIN_MUST_HAVE),
                                    missingMustHave -> recruiter(missingMustHave, outcome, review,
                                            "Missing a must-have; confirm decline"))
                            .doElseIf(fit.jsonPath("$.composite").isLessThan(MIN_COMPOSITE),
                                    weakFit -> recruiter(weakFit, outcome, review, "Weak composite fit; confirm decline"))
                            .doElse(strongFit -> {
                                // Only candidates who pass so far are worth a paid verification check.
                                verification.assign(strongFit.execute(FETCH_EMPLOYMENT_VERIFICATION, applicationId));

                                // Call 3: does the resume match what employers have on record?
                                history.assign(decide(
                                        strongFit.execute(VERIFY_HISTORY + engine, application, verification)));

                                // Call 4: earlier answers and the composite score become this call's state.
                                recommendation.assign(decide(strongFit.execute(
                                        RECOMMEND_NEXT_STEP + engine, role, fit, requirements, history)));

                                route(strongFit, recommendation, outcome, review, applicationId);
                            });
                });
    }

    /** Confidence-gated routing: the higher the stakes of a step, the more confidence it needs. */
    private static void route(
            WorkflowThread thread,
            WfRunVariable recommendation,
            WfRunVariable outcome,
            WfRunVariable review,
            WfRunVariable applicationId) {
        WfRunVariable decision = recommendation.jsonPath("$.decision");
        WfRunVariable confidence = recommendation.jsonPath("$.confidence");
        thread.doIf(confidence.isLessThan(MIN_RECOMMENDATION_CONFIDENCE),
                        unsure -> recruiter(unsure, outcome, review, "Low-confidence recommendation"))
                .doElseIf(decision.isEqualTo("FAST_TRACK_ONSITE").and(confidence.isGreaterThan(ONSITE_CONFIDENCE)),
                        fastTrack -> {
                            outcome.assign("ONSITE");
                            fastTrack.execute(SCHEDULE_INTERVIEW, applicationId, "onsite");
                        })
                // A lower-confidence fast-track is downgraded to a phone screen.
                .doElseIf(decision.isEqualTo("FAST_TRACK_ONSITE").or(decision.isEqualTo("PHONE_SCREEN")),
                        phoneScreen -> {
                            outcome.assign("PHONE_SCREEN");
                            phoneScreen.execute(SCHEDULE_INTERVIEW, applicationId, "phone-screen");
                        })
                .doElseIf(decision.isEqualTo("DECLINE"),
                        declineSuggested -> recruiter(declineSuggested, outcome, review,
                                "Model recommends decline; confirm"))
                .doElse(needsReview -> recruiter(needsReview, outcome, review, "Recruiter review recommended"));
    }

    private static TaskNodeOutput decide(TaskNodeOutput task) {
        return task.timeout(60).withRetries(2);
    }

    private static void recruiter(
            WorkflowThread thread, WfRunVariable outcome, WfRunVariable review, String reason) {
        outcome.assign("RECRUITER_REVIEW");
        UserTaskOutput task = thread.assignUserTask(RECRUITER_REVIEW, null, "recruiting").withNotes(reason);
        // The user task triggers its own reviewer: Jev, acting as the recruiter, completes it.
        thread.scheduleReminderTask(task, 0, COMPLETE_RECRUITER_REVIEW);
        review.assign(task);
    }
}
