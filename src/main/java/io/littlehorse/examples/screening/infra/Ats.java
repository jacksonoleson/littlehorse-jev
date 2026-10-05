package io.littlehorse.examples.screening.infra;

import java.util.List;
import java.util.Map;

/** Applicant tracking system: job applications (with PII) and open roles. */
public final class Ats {

    private Ats() {}

    private static final Map<String, Map<String, Object>> APPLICATIONS = Map.of(
            "APP-101", application("APP-101", "Priya Raman", "priya@example.com",
                    "Backend engineer with 8 years building payments and search systems.",
                    List.of(
                            job("Stripe", "Senior Software Engineer", "2020-03", "present",
                                    "Designed a Go ledger service handling 40k writes/sec across 3 regions",
                                    "Moved payment retries to Kafka; mentored 3 engineers"),
                            job("Etsy", "Software Engineer", "2016-06", "2020-02",
                                    "Built the Python search-indexing pipeline")),
                    List.of("Go", "Python", "Kafka", "PostgreSQL", "Kubernetes"),
                    "I'd love to join as a senior backend engineer. Happy to work from the Portland office."),
            "APP-103", application("APP-103", "Jordan Blake", "jordan@example.com",
                    "Staff engineer who architected core Google Cloud infrastructure.",
                    List.of(
                            job("Google", "Staff Software Engineer", "2019-01", "2024-06",
                                    "Architected a Spanner-backed metadata service in Go; led 10 engineers"),
                            job("Startup Inc", "Software Engineer", "2016-01", "2018-12",
                                    "Built REST APIs in Python")),
                    List.of("Go", "Python", "Spanner", "Distributed systems"),
                    "Excited to bring my staff-level experience to your backend team. Onsite is fine."),
            "APP-104", application("APP-104", "Taylor Kim", "taylor@example.com",
                    "Frontend developer passionate about pixel-perfect UIs.",
                    List.of(job("Pixel Agency", "Junior Frontend Developer", "2025-01", "present",
                            "Built React landing pages")),
                    List.of("React", "CSS", "Figma"),
                    "I'd love any role on your team!"),
            "APP-105", application("APP-105", "Win Big", "win@crypto-gains.example",
                    "BEST CRYPTO SIGNALS 1000% RETURNS GUARANTEED",
                    List.of(),
                    List.of("crypto", "signals", "profit"),
                    "Visit www.crypto-gains.example for guaranteed profit. Limited spots!!!"),
            "APP-106", application("APP-106", "Sam Ortiz", "sam@example.com",
                    "Data engineer building analytics pipelines at consumer scale.",
                    List.of(job("Spotify", "Data Engineer", "2019-04", "present",
                            "Built Airflow pipelines loading 2 TB/day into BigQuery; owns the dbt models")),
                    List.of("Python", "SQL", "Airflow", "BigQuery", "dbt"),
                    "I'm only considering fully remote positions at this point."));

    private static final Map<String, Map<String, Object>> ROLES = Map.of(
            "SENIOR_BACKEND", role("Senior Backend Engineer", 3,
                    List.of("At least 5 years building backend services professionally",
                            "Production experience with Python or Go",
                            "Has designed or operated distributed systems"),
                    List.of("Kafka or another event streaming platform", "Has mentored other engineers"),
                    Map.of("language_depth", 0.35, "system_design", 0.35, "team_leadership", 0.10,
                            "data_engineering", 0.0, "generalist", 0.20)),
            "DATA_ENGINEER", role("Data Engineer", 2,
                    List.of("Has built production data pipelines",
                            "Strong SQL",
                            "Has used a cloud data warehouse such as Snowflake, BigQuery, or Redshift"),
                    List.of("Airflow or Dagster", "dbt"),
                    Map.of("language_depth", 0.25, "system_design", 0.20, "team_leadership", 0.05,
                            "data_engineering", 0.45, "generalist", 0.05)));

    public static Map<String, Object> application(String applicationId) {
        Map<String, Object> application = APPLICATIONS.get(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("No application " + applicationId);
        }
        return application;
    }

    public static Map<String, Object> role(String track) {
        Map<String, Object> role = ROLES.get(track);
        if (role == null) {
            throw new IllegalArgumentException("No open role for track " + track);
        }
        return role;
    }

    private static Map<String, Object> application(
            String id, String name, String email, String summary, List<Map<String, Object>> experience,
            List<String> skills, String coverLetter) {
        return Map.of(
                "application_id", id,
                "candidate", Map.of("name", name, "email", email),
                "resume", Map.of("summary", summary, "experience", experience, "skills", skills),
                "cover_letter", coverLetter);
    }

    private static Map<String, Object> job(String employer, String title, String start, String end, String... highlights) {
        return Map.of("employer", employer, "title", title, "start", start, "end", end, "highlights", List.of(highlights));
    }

    private static Map<String, Object> role(
            String title, int minSeniority, List<String> mustHaves, List<String> niceToHaves, Map<String, Double> weights) {
        return Map.of(
                "title", title,
                "onsite", true,
                "min_seniority", minSeniority,
                "must_haves", mustHaves,
                "nice_to_haves", niceToHaves,
                "weights", weights);
    }
}
