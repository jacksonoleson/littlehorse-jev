package io.littlehorse.examples.screening.policy;

import io.littlehorse.shared.jev.SystemOne.Question;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every model question and threshold in candidate screening. Review and tune here. Per-role
 * requirements and composite weights are role data and live in the ATS.
 */
public final class ScreeningPolicy {

    private ScreeningPolicy() {}

    // ---- Thresholds ----

    /** Above this, the application is closed as spam. */
    public static final double SPAM = 0.8;
    /** Below this, a recruiter picks the role. */
    public static final double MIN_TRACK_CONFIDENCE = 0.6;
    /** Above this, a remote-only candidate conflicts with an onsite role. */
    public static final double REMOTE_ONLY = 0.7;
    /** How far below the role's minimum seniority level a candidate may score. */
    public static final double SENIORITY_SLACK = 0.5;
    /** Any must-have below this means the candidate is missing it. */
    public static final double MIN_MUST_HAVE = 0.5;
    /** Composite fit below this is weak. */
    public static final double MIN_COMPOSITE = 0.45;
    /** Below this, a recruiter decides the next step. */
    public static final double MIN_RECOMMENDATION_CONFIDENCE = 0.6;
    /** A fast-track needs this much confidence to book an onsite; otherwise it becomes a phone screen. */
    public static final double ONSITE_CONFIDENCE = 0.85;

    // ---- Questions ----

    /** Skill dimensions, scored speculatively for every candidate; the role's weights pick what matters. */
    public static final Map<String, Question> DIMENSIONS = Map.of(
            "language_depth", Question.score(
                    "How much depth in Python or Go does `application.resume` show?",
                    List.of("Neither Python nor Go mentioned", "Mentioned but no detail",
                            "Used in projects, some specifics", "Primary language, multiple projects",
                            "Deep expertise: architecture, performance, libraries")),
            "system_design", Question.score(
                    "How much experience designing large-scale or distributed systems does `application.resume` show?",
                    List.of("No architecture work mentioned", "Contributed to design discussions",
                            "Designed components of a larger system", "Owned architecture of a significant system",
                            "Designed systems at scale across multiple domains")),
            "team_leadership", Question.score(
                    "How much experience managing or leading engineering teams does `application.resume` show?",
                    List.of("No management experience mentioned", "Informal mentorship or tech lead role",
                            "Led a small team or project", "Managed a team with direct reports",
                            "Managed multiple teams or an engineering org")),
            "data_engineering", Question.score(
                    "How much data-engineering experience does `application.resume` show?",
                    List.of("No data pipeline or warehouse work", "Ran queries or reports",
                            "Built some ETL jobs or pipelines", "Owns production pipelines and data models",
                            "Designed data platforms at scale")),
            "generalist", Question.score(
                    "How much evidence does `application.resume` show of picking up unfamiliar tools, roles, or"
                            + " domains outside the candidate's core specialty?",
                    List.of("Only one domain or role mentioned", "Some variety but within a narrow field",
                            "Worked across a few different areas or tech stacks",
                            "Regularly moved between domains, wore many hats",
                            "Track record of ramping up in unfamiliar areas and delivering")));

    /** Call 1. State: {@code application}. */
    public static final Map<String, Question> TRIAGE_QUESTIONS = triageQuestions();

    /** Call 2 asks this once per must-have and nice-to-have of the chosen role. State: {@code application}. */
    public static Question requirementQuestion(String requirement) {
        return Question.noul(Map.of(
                "question", "Does `application.resume` show that the candidate meets `requirement`?",
                "requirement", requirement));
    }

    /** Call 3. State: {@code resume_experience}, {@code employment_verification}. */
    public static final Map<String, Question> VERIFY_QUESTIONS = Map.of(
            "employers_match", Question.noul(
                    "Does every employer in `resume_experience` appear in `employment_verification.records`?"),
            "titles_match", Question.noul("Do the titles in `resume_experience` match"
                    + " `employment_verification.records` without overstating seniority?"),
            "dates_match", Question.noul(
                    "Are the dates in `resume_experience` within a few months of `employment_verification.records`?"),
            "discrepancy", Question.choice(
                    "What is the most serious discrepancy between `resume_experience` and `employment_verification`?",
                    Map.of(
                            "NONE", "Employers, titles, and dates all match",
                            "TITLE_INFLATION", "A claimed title is more senior than the record",
                            "DATE_MISMATCH", "Dates are off by more than a few months",
                            "UNVERIFIED", "An employer has no record, or no records were found")));

    /** Call 4. State: {@code role}, {@code screening} (every earlier answer plus the composite). */
    public static final Map<String, Question> NEXT_STEP_QUESTIONS = Map.of(
            "next_step", Question.choice(
                    "Given `screening`, what is the right next step for this candidate for `role`?",
                    Map.of(
                            "FAST_TRACK_ONSITE", "Composite fit of at least 0.6, every must-have clearly met, and no"
                                    + " verification discrepancy",
                            "PHONE_SCREEN", "Solid fit with minor gaps, and no verification discrepancy",
                            "RECRUITER_REVIEW", "Any verification discrepancy, or the evidence is mixed",
                            "DECLINE", "Clearly misses the role's must-haves")));

    /**
     * Asked by the Jev "recruiter" in the recruiting tool to complete the recruiter-review user task.
     * State: every WfRun variable, plus {@code review_reason} (the user task's notes).
     */
    public static final Map<String, Question> RECRUITER_REVIEW_QUESTIONS = Map.of(
            "decision", Question.choice(
                    "As the recruiter, given `review_reason` and the screening results, what should happen to"
                            + " this candidate?",
                    Map.of(
                            "ADVANCE", "Move the candidate to an interview: a real fit for an open role",
                            "HOLD", "Keep the application open: promising, but something needs a follow-up first",
                            "DECLINE", "Reject: no open role fits, a must-have is missing, or the role's conditions"
                                    + " can't be met")));

    private static Map<String, Question> triageQuestions() {
        Map<String, Question> q = new LinkedHashMap<>(DIMENSIONS);
        q.put("track", Question.choice(
                Map.of("question", "Which open role best fits the candidate in `application`?",
                        "focus", "Match the candidate's actual experience, not the roles they say they want."),
                Map.of(
                        "SENIOR_BACKEND", Map.of(
                                "what", "Senior backend engineer: designs, builds, and scales services, usually in"
                                        + " Python or Go",
                                "not_for", "Frontend-only, data-pipeline-focused, or people-management careers"),
                        "ENGINEERING_MANAGER", Map.of(
                                "what", "Engineering manager: leads teams with direct reports, hires, and runs planning",
                                "not_for", "Individual contributors, even senior ones who mentor"),
                        "DATA_ENGINEER", Map.of(
                                "what", "Data engineer: builds data pipelines, warehouses, and analytics data models",
                                "not_for", "General backend services or data science"),
                        "NOT_A_FIT", Map.of(
                                "what", "None of these roles fit, e.g. frontend, design, or unrelated careers"))));
        q.put("seniority", Question.score(
                "How senior is the candidate in `application`, based on years and scope of professional work?",
                List.of("Intern or new grad", "Junior (1-3 years)", "Mid-level (3-6 years)",
                        "Senior (6-10 years)", "Staff or above (10+ years)")));
        q.put("spam", Question.noul("Is `application` spam, auto-generated filler, or missing any real work history?"));
        q.put("remote_only", Question.noul(
                "Does `application.cover_letter` say the candidate will only accept fully remote work?"));
        return Map.copyOf(q);
    }
}
