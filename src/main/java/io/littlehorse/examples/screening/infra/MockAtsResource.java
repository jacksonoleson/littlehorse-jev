package io.littlehorse.examples.screening.infra;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;
import java.util.Map;

/** Fake applicant tracking system: job applications (with PII) and open roles. */
@Path("/mock/ats")
public class MockAtsResource {

    private static final Map<String, Map<String, Object>> APPLICATIONS = Map.of(
            "APP-101", application("APP-101", "Priya Raman", "priya@example.com",
                    "Backend engineer with 8 years building payments and search systems.",
                    List.of(
                            job("Stripe", "Senior Software Engineer", "2020-03", "present",
                                    "Designed a Go ledger service handling 40k writes/sec across 3 regions",
                                    "Led the migration of payment retries to Kafka; mentored 3 engineers",
                                    "Wrote Python tooling for capacity planning"),
                            job("Etsy", "Software Engineer", "2016-06", "2020-02",
                                    "Built the Python search-indexing pipeline",
                                    "On call for checkout services")),
                    List.of("Go", "Python", "Kafka", "PostgreSQL", "Kubernetes", "gRPC"),
                    "I'd love to join as a senior backend engineer. Happy to work from the Portland office."),
            "APP-102", application("APP-102", "Marcus Lee", "marcus@example.com",
                    "Engineering manager who has grown platform teams from 4 to 14 engineers.",
                    List.of(
                            job("Atlassian", "Engineering Manager", "2019-01", "present",
                                    "Managed two teams (14 engineers) building the notification platform",
                                    "Hired 9 engineers and promoted 3 to senior",
                                    "Ran quarterly planning and incident reviews"),
                            job("Shopify", "Senior Software Engineer", "2013-05", "2018-12",
                                    "Built Ruby and Python checkout services",
                                    "Tech lead for a 4-person team")),
                    List.of("People management", "Hiring", "Python", "Ruby", "AWS"),
                    "Looking for my next engineering manager role. Hybrid or onsite both work for me."),
            "APP-103", application("APP-103", "Jordan Blake", "jordan@example.com",
                    "Staff engineer who architected core Google Cloud infrastructure.",
                    List.of(
                            job("Google", "Staff Software Engineer", "2019-01", "2024-06",
                                    "Architected a Spanner-backed metadata service used across Cloud",
                                    "Led a team of 10 engineers",
                                    "Python and Go"),
                            job("Startup Inc", "Software Engineer", "2016-01", "2018-12",
                                    "Built REST APIs in Python")),
                    List.of("Go", "Python", "Spanner", "Distributed systems"),
                    "Excited to bring my staff-level experience to your backend team. Onsite is fine."),
            "APP-104", application("APP-104", "Taylor Kim", "taylor@example.com",
                    "Frontend developer passionate about pixel-perfect UIs.",
                    List.of(job("Pixel Agency", "Junior Frontend Developer", "2025-01", "present",
                            "Built React landing pages", "Wrote CSS animations")),
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
                            "Built Airflow pipelines loading 2 TB/day into BigQuery",
                            "Owns the dbt models behind the listening-analytics dashboards",
                            "Python and advanced SQL")),
                    List.of("Python", "SQL", "Airflow", "BigQuery", "dbt"),
                    "I'm only considering fully remote positions at this point."),
            "APP-107", application("APP-107", "Alex Novak", "alex@example.com",
                    "ETL developer in banking.",
                    List.of(job("First Regional Bank", "ETL Developer", "2017-02", "present",
                            "Maintains nightly Informatica jobs on an on-prem Hadoop cluster",
                            "Writes Hive and SQL reports for the risk team")),
                    List.of("SQL", "Hive", "Informatica", "Hadoop"),
                    "Interested in your data engineering role. Onsite works for me."));

    private static final Map<String, Map<String, Object>> ROLES = Map.of(
            "SENIOR_BACKEND", role("Senior Backend Engineer", 3,
                    List.of("At least 5 years building backend services professionally",
                            "Production experience with Python or Go",
                            "Has designed or operated distributed systems"),
                    List.of("Kafka or another event streaming platform", "Has mentored other engineers"),
                    Map.of("language_depth", 0.35, "system_design", 0.35, "team_leadership", 0.10,
                            "data_engineering", 0.0, "generalist", 0.20)),
            "ENGINEERING_MANAGER", role("Engineering Manager", 3,
                    List.of("Has managed engineers as their direct manager",
                            "Has hired engineers",
                            "Has a hands-on software engineering background"),
                    List.of("Has managed more than one team"),
                    Map.of("language_depth", 0.10, "system_design", 0.20, "team_leadership", 0.45,
                            "data_engineering", 0.0, "generalist", 0.25)),
            "DATA_ENGINEER", role("Data Engineer", 2,
                    List.of("Has built production data pipelines",
                            "Strong SQL",
                            "Has used a cloud data warehouse such as Snowflake, BigQuery, or Redshift"),
                    List.of("Airflow or Dagster", "dbt"),
                    Map.of("language_depth", 0.25, "system_design", 0.20, "team_leadership", 0.05,
                            "data_engineering", 0.45, "generalist", 0.05)));

    @GET
    @Path("/applications/{applicationId}")
    public Map<String, Object> application(@PathParam("applicationId") String applicationId) {
        Map<String, Object> application = APPLICATIONS.get(applicationId);
        if (application == null) {
            throw new NotFoundException();
        }
        return application;
    }

    @GET
    @Path("/roles/{track}")
    public Map<String, Object> role(@PathParam("track") String track) {
        Map<String, Object> role = ROLES.get(track);
        if (role == null) {
            throw new NotFoundException();
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
