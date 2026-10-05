package io.littlehorse.examples.screening.infra;

import java.util.List;
import java.util.Map;

/** Employment-verification vendor: what past employers actually have on record. */
public final class EmploymentRecords {

    private EmploymentRecords() {}

    private static final Map<String, List<Map<String, String>>> RECORDS = Map.of(
            "APP-101", List.of(
                    record("Stripe", "Senior Software Engineer", "2020-03", "present"),
                    record("Etsy", "Software Engineer", "2016-06", "2020-02")),
            "APP-102", List.of(
                    record("Atlassian", "Engineering Manager", "2019-01", "present"),
                    record("Shopify", "Senior Software Engineer", "2013-05", "2018-12")),
            "APP-103", List.of(
                    record("Google", "Software Engineer II", "2021-03", "2023-08"),
                    record("Startup Inc", "Software Engineer", "2016-01", "2018-12")),
            "APP-106", List.of(record("Spotify", "Data Engineer", "2019-04", "present")),
            "APP-107", List.of(record("First Regional Bank", "ETL Developer", "2017-02", "present")));

    public static Map<String, Object> verify(String applicationId) {
        List<Map<String, String>> records = RECORDS.get(applicationId);
        return records == null
                ? Map.of("status", "NO_RECORDS", "records", List.of())
                : Map.of("status", "COMPLETED", "records", records);
    }

    private static Map<String, String> record(String employer, String title, String start, String end) {
        return Map.of("employer", employer, "title", title, "start", start, "end", end);
    }
}
