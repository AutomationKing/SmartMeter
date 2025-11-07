package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Flaky test analyzer that reads Cucumber JSON report (recommended).
 * - Configure Cucumber to write JSON (e.g. --plugin json:target/cucumber.json)
 * - Set system property cucumber.json.path to point to JSON (optional)
 *
 * Produces:
 * - test-history/test-history.json (history across runs)
 * - test-history/test-report.html (HTML report)
 */
public class FlakyTestAnalyzer implements TestExecutionListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile = new File("test-history/test-history.json");
    private final File reportFile = new File("test-history/test-report.html");

    // In-memory summary for this run
    private final List<TestSummary> thisRunSummaries = new ArrayList<>();
    private int total = 0, passed = 0, flaky = 0, failed = 0;

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            // 1) locate cucumber JSON
            String jsonPath = System.getProperty("cucumber.json.path", "target/cucumber.json");
            File jsonFile = new File(jsonPath);
            if (!jsonFile.exists()) {
                System.out.println("⚠️ Cucumber JSON not found at " + jsonFile.getAbsolutePath() +
                        " — falling back to console parsing not implemented here.");
                return;
            }

            // 2) parse cucumber json and collect scenarios
            Map<String, ScenarioResult> scenarioResults = parseCucumberJson(jsonFile);

            // 3) load history
            ObjectNode historyRoot = historyFile.exists()
                    ? (ObjectNode) mapper.readTree(historyFile)
                    : mapper.createObjectNode();
            if (!historyRoot.has("tests")) historyRoot.set("tests", mapper.createObjectNode());

            ObjectNode testsNode = (ObjectNode) historyRoot.get("tests");

            // 4) evaluate each scenario -> update history and produce summary
            for (Map.Entry<String, ScenarioResult> e : scenarioResults.entrySet()) {
                String key = e.getKey();                 // e.g. classpath:...feature:1466
                ScenarioResult r = e.getValue();

                // historical stats
                TestStats stats = readStatsForKey(testsNode, key);

                boolean nowPassed = r.status == Status.PASSED;
                boolean isFlaky = !nowPassed && stats.passCount > 0;

                // update counters
                total++;
                if (nowPassed) {
                    passed++;
                } else if (isFlaky) {
                    flaky++;
                } else {
                    failed++;
                }

                // update history entry for this run
                ArrayNode historyArray = testsNode.has(key)
                        ? (ArrayNode) testsNode.get(key)
                        : mapper.createArrayNode();

                ObjectNode entry = mapper.createObjectNode();
                entry.put("timestamp", LocalDateTime.now().toString());
                entry.put("status", nowPassed ? "SUCCESSFUL" : (isFlaky ? "FLAKY" : "FAILED"));
                entry.put("reason", r.errorMessage == null ? (nowPassed ? "Passed" : "Failed") : r.errorMessage);
                entry.put("durationMs", r.durationMs);
                entry.put("flakyPattern", isFlaky);

                historyArray.add(entry);
                testsNode.set(key, historyArray);

                // Keep summary row
                thisRunSummaries.add(new TestSummary(key, entry.get("reason").asText(), stats.lastPassedDate, nowPassed ? "PASSED" : (isFlaky ? "FLAKY" : "FAILED")));
            }

            // 5) write history back
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, historyRoot);

            // 6) generate html report
            generateHtmlReport();

            System.out.println("✅ FlakyTestAnalyzer: report at " + reportFile.getAbsolutePath());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Parses Cucumber JSON and builds map key -> ScenarioResult
    private Map<String, ScenarioResult> parseCucumberJson(File jsonFile) throws IOException {
        Map<String, ScenarioResult> map = new LinkedHashMap<>();
        JsonNode root = mapper.readTree(jsonFile);

        if (!root.isArray()) return map;

        for (JsonNode featureNode : root) {
            // feature uri (could be file path or classpath)
            String uri = featureNode.path("uri").asText(null);
            if (uri == null || uri.isEmpty()) {
                // older cucumber JSON may use 'path' or 'name' - try 'uri' first, else skip
                uri = featureNode.path("path").asText(null);
            }

            JsonNode elements = featureNode.path("elements");
            if (!elements.isArray()) continue;
            for (JsonNode element : elements) {
                // element is a scenario/outline example
                String type = element.path("type").asText("");
                if (!"scenario".equalsIgnoreCase(type) && !"scenario_outline".equalsIgnoreCase(type)) {
                    // still include scenario_outline elements if present
                    // continue if you want to skip hooks etc.
                }

                // element may have a "line" attribute
                int line = element.path("line").asInt(-1);
                String name = element.path("name").asText("");

                // Build key similar to rerun.txt: classpath:path/to.feature:line
                // If uri is an absolute file path, convert to classpath-like string if you want.
                // We'll keep raw uri (it commonly is 'classpath:functionalTest/...feature' in some setups)
                String key;
                if (uri != null && !uri.isEmpty() && line > 0) {
                    key = uri + ":" + line;
                } else if (uri != null && !uri.isEmpty()) {
                    key = uri;
                } else {
                    // fallback: name + hash
                    key = name + "@" + UUID.randomUUID().toString();
                }

                // Determine status and error message from steps
                Status finalStatus = Status.PASSED;
                String errorMsg = null;
                long durationMs = 0;

                JsonNode steps = element.path("steps");
                if (steps.isArray()) {
                    for (JsonNode step : steps) {
                        JsonNode result = step.path("result");
                        String statusS = result.path("status").asText("");
                        if (result.has("duration")) {
                            // cucumber-jvm writes duration in ns sometimes, but it depends on version; keep as 0 if absent
                            long d = result.path("duration").asLong(0);
                            // optionally convert nanoseconds -> ms if necessary
                            durationMs += d;
                        }
                        if ("failed".equalsIgnoreCase(statusS)) {
                            finalStatus = Status.FAILED;
                            // pick first non-empty error_message
                            String em = result.path("error_message").asText(null);
                            if (em != null && !em.isEmpty()) {
                                errorMsg = extractConciseError(em);
                                break; // first failure sufficient
                            }
                        } else if (!"passed".equalsIgnoreCase(statusS) && finalStatus != Status.FAILED) {
                            // treat anything else as non-passed (pending/skipped)
                            finalStatus = Status.FAILED;
                        }
                    }
                }

                ScenarioResult sr = new ScenarioResult(finalStatus, errorMsg, durationMs);
                map.put(key, sr);
            }
        }

        return map;
    }

    private String extractConciseError(String full) {
        if (full == null) return null;
        // pick first non-empty line and truncate to 200 chars
        String[] lines = full.split("\\r?\\n");
        for (String l : lines) {
            l = l.trim();
            if (!l.isEmpty()) {
                return l.length() > 200 ? l.substring(0, 200) + "..." : l;
            }
        }
        return full.length() > 200 ? full.substring(0, 200) + "..." : full;
    }

    private TestStats readStatsForKey(ObjectNode testsNode, String key) {
        TestStats stats = new TestStats();
        if (testsNode == null || !testsNode.has(key)) return stats;
        JsonNode history = testsNode.get(key);
        if (!history.isArray()) return stats;
        for (JsonNode e : history) {
            String status = e.path("status").asText("");
            if ("SUCCESSFUL".equals(status)) {
                stats.passCount++;
                stats.lastPassedDate = e.path("timestamp").asText(null);
            } else {
                stats.failCount++;
            }
        }
        return stats;
    }

    private void generateHtmlReport() throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<title>Test Report</title><style>")
            .append("body{font-family:Arial;margin:20px;}table{border-collapse:collapse;width:100%;}")
            .append("th,td{border:1px solid #ccc;padding:8px;text-align:left;}th{background:#333;color:#fff;}")
            .append(".PASSED{background:#d4edda}.FLAKY{background:#fff3cd}.FAILED{background:#f8d7da}")
            .append("</style></head><body>");
        html.append("<h1>Test Execution Report</h1>");
        html.append(String.format("<p><b>Total:</b> %d | <b>Passed:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</p>",
                total, passed, flaky, failed));
        html.append("<table><tr><th>Test</th><th>Status</th><th>Last Passed</th><th>Reason</th></tr>");
        for (TestSummary s : thisRunSummaries) {
            html.append("<tr class='").append(s.status).append("'>")
                .append("<td>").append(escapeHtml(s.name)).append("</td>")
                .append("<td>").append(s.status).append("</td>")
                .append("<td>").append(s.lastPassDate == null ? "-" : s.lastPassDate).append("</td>")
                .append("<td>").append(escapeHtml(s.lastFailureReason == null ? "-" : s.lastFailureReason)).append("</td>")
                .append("</tr>");
        }
        html.append("</table></body></html>");

        if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
        Files.writeString(Path.of(reportFile.toURI()), html.toString());
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ---------------- helper classes ----------------
    private static class ScenarioResult {
        final Status status;
        final String errorMessage;
        final long durationMs;
        ScenarioResult(Status status, String errorMessage, long durationMs) {
            this.status = status;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
        }
    }

    private enum Status { PASSED, FAILED }

    private static class TestStats {
        int passCount = 0;
        int failCount = 0;
        String lastPassedDate = null;
    }

    private static class TestSummary {
        final String name;
        final String lastFailureReason;
        final String lastPassDate;
        final String status;
        TestSummary(String name, String lastFailureReason, String lastPassDate, String status) {
            this.name = name; this.lastFailureReason = lastFailureReason; this.lastPassDate = lastPassDate; this.status = status;
        }
    }
}
