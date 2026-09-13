package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The money alert rules, checked as rules rather than as a file that exists.
 *
 * <p>This class used to assert only that {@code money.yml} was present on disk. That is what let
 * five money alerts ship querying series registered as constant zeros in
 * {@code ReconciliationService}'s constructor -- they read as configured and could never fire.
 * Deleting a {@code runbook_url} from an alert also left it green; found 2026-09-09 performing
 * Phase 7's break-test 2.
 */
class PrometheusRulesTest {

    private static final Path RULES = Paths.get("../Deployment/prometheus/rules/money.yml");
    private static final Path RUNBOOK = Paths.get("../RandomDocuments/MoneyFlowReview_2026-09-04/RUNBOOK.md");

    /**
     * Services whose src/main may register a money metric.
     *
     * <p>CommonLibrary became an aggregator of six modules on 2026-09-12, so
     * {@code ../CommonLibrary/src/main} no longer exists and this test stopped seeing
     * {@code money_outbox_backlog_age_seconds}, which {@code OutboxBacklogMetrics} registers from
     * what is now {@code common-messaging}. Listing the aggregator directory rather than each module
     * keeps the walk correct if the modules are ever renamed or added to — the walk below recurses.
     */
    private static final List<String> PRODUCERS = List.of(
            "../LedgerService/src/main", "../CustomerApplication/src/main",
            "../WalletService/src/main", "../PaymentGatewayIntegration/src/main",
            "../CommonLibrary");

    private record Alert(String name, String expr, String forDuration, Map<String, Object> labels,
                         Map<String, Object> annotations) {
    }

    @SuppressWarnings("unchecked")
    private static List<Alert> alerts() {
        Map<String, Object> doc;
        try {
            doc = new Yaml().load(Files.readString(RULES, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Alert> out = new ArrayList<>();
        for (Map<String, Object> group : (List<Map<String, Object>>) doc.get("groups")) {
            for (Map<String, Object> rule : (List<Map<String, Object>>) group.getOrDefault("rules", List.of())) {
                if (rule.get("alert") == null) {
                    continue;
                }
                out.add(new Alert(
                        String.valueOf(rule.get("alert")),
                        String.valueOf(rule.get("expr")),
                        rule.get("for") == null ? null : String.valueOf(rule.get("for")),
                        (Map<String, Object>) rule.getOrDefault("labels", new LinkedHashMap<>()),
                        (Map<String, Object>) rule.getOrDefault("annotations", new LinkedHashMap<>())));
            }
        }
        return out;
    }

    private static void requireDeployment() {
        assumeTrue(Files.exists(RULES), "Deployment directory not present; skipping");
    }

    @Test
    void thereAreMoneyAlerts() {
        requireDeployment();
        assertTrue(alerts().size() >= 5, "expected the money alert set, found " + alerts().size());
    }

    /** An operator paged at 03:00 needs the page to say what to do. */
    @Test
    void everyAlertLinksARunbook() {
        requireDeployment();
        List<String> without = alerts().stream()
                .filter(a -> !(a.annotations().get("runbook_url") instanceof String s) || s.isBlank())
                .map(Alert::name).collect(Collectors.toList());
        assertTrue(without.isEmpty(), "alerts with no runbook_url: " + without);
    }

    /** And the runbook needs a section for it, or the link is a 404. */
    @Test
    void theRunbookHasASectionPerAlert() {
        requireDeployment();
        assumeTrue(Files.exists(RUNBOOK), "runbook not present; skipping");
        String txt;
        try {
            txt = Files.readString(RUNBOOK, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> missing = alerts().stream()
                .map(Alert::name)
                .filter(n -> !Pattern.compile("^##\\s+" + Pattern.quote(n) + "\\b", Pattern.MULTILINE)
                        .matcher(txt).find())
                .collect(Collectors.toList());
        assertTrue(missing.isEmpty(), "runbook has no section for: " + missing);
    }

    /** A severity label, or the alert cannot be routed to anyone. */
    @Test
    void everyAlertCarriesASeverityAndADuration() {
        requireDeployment();
        List<String> bad = alerts().stream()
                .filter(a -> a.labels().get("severity") == null || a.forDuration() == null)
                .map(Alert::name).collect(Collectors.toList());
        assertTrue(bad.isEmpty(), "alerts missing severity or for: " + bad);
    }

    /**
     * The one that matters: every series an alert queries must have a producer in src/main.
     *
     * <p>An alert on a metric nothing exports is silence dressed as coverage.
     */
    @Test
    void everyMetricQueriedHasAProducer() {
        requireDeployment();

        Set<String> registered = new java.util.HashSet<>();
        Pattern metric = Pattern.compile("\"(money_[a-z0-9_]+|payment_[a-z0-9_]+|payout_[a-z0-9_]+)\"");
        for (String root : PRODUCERS) {
            Path p = Paths.get(root);
            if (!Files.exists(p)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(p)) {
                files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                    try {
                        Matcher m = metric.matcher(Files.readString(f, StandardCharsets.UTF_8));
                        while (m.find()) {
                            registered.add(m.group(1));
                        }
                    } catch (IOException ignored) {
                        // unreadable source is not a rule failure
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Pattern series = Pattern.compile("\\b(money_[a-z0-9_]+|payment_[a-z0-9_]+|payout_[a-z0-9_]+)\\b");
        List<String> orphans = new ArrayList<>();
        for (Alert a : alerts()) {
            Matcher m = series.matcher(a.expr());
            while (m.find()) {
                String name = m.group(1);
                // Micrometer suffixes a timer/summary/counter on the way to Prometheus.
                String base = name.replaceAll("_(total|sum|count|bucket|seconds|days)$", "");
                if (!registered.contains(name) && !registered.contains(base)) {
                    orphans.add(a.name() + " -> " + name);
                }
            }
        }
        assertTrue(orphans.isEmpty(),
                "alerts querying a series no src/main registers (they can never fire): " + orphans
                + "\nregistered: " + new java.util.TreeSet<>(registered));
    }
}
