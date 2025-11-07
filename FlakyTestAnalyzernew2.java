package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enhanced Flaky Test Analyzer — reads Cucumber JSON and builds history.
 * Features:
 * ✅ Flaky detection
 * ✅ Pass %
 * ✅ Trend icons (last 5 runs)
 * ✅ Interactive HTML filters
 * ✅ Pie chart summary
 */
public class FlakyTestAnalyzer implements TestExecutionListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile = new File("test-history/test-history.json");
    private final File reportFile = new File("test-history/test-report.html");

    private final List<TestSummary> thisRunSummaries = new ArrayList<>();
    private ObjectNode testsNode;
    private int total = 0, passed = 0, flaky = 0, failed = 0;

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            String jsonPath = System.getProperty("cucumber.json.path", "target/cucumber.json");
            File jsonFile = new File(jsonPath);
            if (!jsonFile.exists()) {
                System.out.println("⚠️ Cucumber JSON not found: " + jsonFile.getAbsolutePath());
                return;
            }

            Map<String, ScenarioResult> scenarioResults = parseCucumberJson(jsonFile);

            ObjectNode historyRoot = historyFile.exists()
                    ? (ObjectNode) mapper.readTree(historyFile)
                    : mapper.createObjectNode();
            if (!historyRoot.has("tests")) historyRoot.set("tests", mapper.createObjectNode());
            testsNode = (ObjectNode) historyRoot.get("tests");

            for (Map.Entry<String, ScenarioResult> e : scenarioResults.entrySet()) {
                String key = e.getKey();
                ScenarioResult r = e.getValue();

                TestStats stats = readStatsForKey(testsNode, key);
                boolean nowPassed = r.status == Status.PASSED;
                boolean isFlaky = !nowPassed && stats.passCount > 0;

                total++;
                if (nowPassed) passed++;
                else if (isFlaky) flaky++;
                else failed++;

                ArrayNode historyArray = testsNode.has(key)
                        ? (ArrayNode) testsNode.get(key)
                        : mapper.createArrayNode();

                ObjectNode entry = mapper.createObjectNode();
                String timestamp = LocalDateTime.now().toString();
                entry.put("timestamp", timestamp);
                entry.put("status", nowPassed ? "SUCCESSFUL" : (isFlaky ? "FLAKY" : "FAILED"));
                entry.put("reason", r.errorMessage == null ? (nowPassed ? "Passed" : "Failed") : r.errorMessage);
                entry.put("durationMs", r.durationMs);
                entry.put("flakyPattern", isFlaky);
                historyArray.add(entry);
                testsNode.set(key, historyArray);

                // Include current run in summary to fix Last Passed and Pass %
                thisRunSummaries.add(new TestSummary(
                        key,
                        entry.get("reason").asText(),
                        nowPassed ? timestamp : stats.lastPassedDate,
                        nowPassed ? "PASSED" : (isFlaky ? "FLAKY" : "FAILED"),
                        stats.passCount + (nowPassed ? 1 : 0),
                        stats.failCount + (!nowPassed && !isFlaky ? 1 : 0)
                ));
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, historyRoot);
            generateHtmlReport();
            System.out.println("✅ FlakyTestAnalyzer: report at " + reportFile.getAbsolutePath());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------------- PARSING ----------------
    private Map<String, ScenarioResult> parseCucumberJson(File jsonFile) throws IOException {
        Map<String, ScenarioResult> map = new LinkedHashMap<>();
        JsonNode root = mapper.readTree(jsonFile);
        if (!root.isArray()) return map;

        for (JsonNode featureNode : root) {
            String uri = featureNode.path("uri").asText(featureNode.path("path").asText(""));
            JsonNode elements = featureNode.path("elements");
            if (!elements.isArray()) continue;

            for (JsonNode element : elements) {
                int line = element.path("line").asInt(-1);
                String name = element.path("name").asText("");
                String key = (uri != null && !uri.isEmpty())
                        ? (line > 0 ? uri + ":" + line : uri)
                        : name + "@" + UUID.randomUUID();

                Status finalStatus = Status.PASSED;
                String errorMsg = null;
                long durationMs = 0;

                JsonNode steps = element.path("steps");
                if (steps.isArray()) {
                    for (JsonNode step : steps) {
                        JsonNode result = step.path("result");
                        String statusS = result.path("status").asText("");
                        durationMs += result.path("duration").asLong(0);
                        if ("failed".equalsIgnoreCase(statusS)) {
                            finalStatus = Status.FAILED;
                            errorMsg = extractConciseError(result.path("error_message").asText(""));
                            break;
                        }
                    }
                }

                map.put(key, new ScenarioResult(finalStatus, errorMsg, durationMs));
            }
        }
        return map;
    }

    private String extractConciseError(String full) {
        if (full == null) return null;
        String[] lines = full.split("\\r?\\n");
        for (String l : lines) {
            if (!l.trim().isEmpty()) return l.length() > 200 ? l.substring(0, 200) + "..." : l;
        }
        return full.length() > 200 ? full.substring(0, 200) + "..." : full;
    }

    // ---------------- REPORT ----------------
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

    private String getRecentHistoryTrend(JsonNode history, int limit) {
        if (history == null || !history.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        int size = history.size();
        for (int i = Math.max(0, size - limit); i < size; i++) {
            String status = history.get(i).path("status").asText("");
            sb.append("SUCCESSFUL".equals(status) ? "✅" : "❌");
        }
        return sb.toString();
    }

    private void generateHtmlReport() throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<title>Test Report</title><style>")
            .append("body{font-family:Arial;margin:20px;}table{border-collapse:collapse;width:100%;}")
            .append("th,td{border:1px solid #ccc;padding:8px;text-align:left;}th{background:#333;color:#fff;}") 
            .append(".PASSED{background:#d4edda}.FLAKY{background:#fff3cd}.FAILED{background:#f8d7da}")
            .append("button{margin-right:5px;padding:5px 10px;border:none;border-radius:4px;cursor:pointer;}")
            .append("</style></head><body>");

        html.append("<h1>Test Execution Report</h1>");
        html.append(String.format("<p><b>Total:</b> %d | <b>Passed:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</p>",
                total, passed, flaky, failed));

        // Filter buttons
        html.append("<p>")
            .append("<button onclick=\"filter('ALL')\">All</button>")
            .append("<button onclick=\"filter('PASSED')\">Passed</button>")
            .append("<button onclick=\"filter('FLAKY')\">Flaky</button>")
            .append("<button onclick=\"filter('FAILED')\">Failed</button>")
            .append("</p>");

        // Responsive Pie Chart
        html.append("<div style='width:400px; max-width:50%; height:400px; margin-bottom:20px;'>")
            .append("<canvas id='summaryChart'></canvas>")
            .append("</div>")
            .append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>")
            .append("<script>")
            .append("const ctx = document.getElementById('summaryChart').getContext('2d');")
            .append("new Chart(ctx, {")
            .append("type: 'pie',")
            .append("data: {")
            .append("labels: ['Passed', 'Flaky', 'Failed'],")
            .append("datasets: [{")
            .append("data: [").append(passed).append(",").append(flaky).append(",").append(failed).append("],")
            .append("backgroundColor: ['#28a745','#ffc107','#dc3545']")
            .append("}]")
            .append("},")
            .append("options: {responsive: true, maintainAspectRatio: false}")
            .append("});")
            .append("</script>");

        // Table
        html.append("<table><tr><th>Test</th><th>Status</th><th>Last Passed</th><th>Reason</th><th>Pass %</th><th>Trend</th></tr>");
        for (TestSummary s : thisRunSummaries) {
            JsonNode history = testsNode.get(s.name);
            double passRate = s.passCount + s.failCount == 0 ? 0 : (s.passCount * 100.0 / (s.passCount + s.failCount));
            String trend = getRecentHistoryTrend(history, 5);
            html.append("<tr class='").append(s.status).append("'>")
                .append("<td>").append(escapeHtml(s.name)).append("</td>")
                .append("<td>").append(s.status).append("</td>")
                .append("<td>").append(s.lastPassDate == null ? "-" : s.lastPassDate).append("</td>")
                .append("<td>").append(escapeHtml(s.lastFailureReason == null ? "-" : s.lastFailureReason)).append("</td>")
                .append("<td>").append(String.format("%.1f%%", passRate)).append("</td>")
                .append("<td>").append(trend).append("</td>")
                .append("</tr>");
        }
        html.append("</table>");

        // JS Filter
        html.append("<script>")
            .append("function filter(status){document.querySelectorAll('tr').forEach(tr=>{")
            .append("if(!tr.classList.contains(status)&&status!=='ALL'&&tr.classList.length)tr.style.display='none';")
            .append("else tr.style.display='';});}")
            .append("</script>");

        html.append("</body></html>");

        if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
        Files.writeString(Path.of(reportFile.toURI()), html.toString());
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // ---------------- HELPERS ----------------
    private static class ScenarioResult {
        final Status status;
        final String errorMessage;
        final long durationMs;
        ScenarioResult(Status status, String errorMessage, long durationMs) {
            this.status = status; this.errorMessage = errorMessage; this.durationMs = durationMs;
        }
    }
    private enum Status { PASSED, FAILED }

    private static class TestStats {
        int passCount = 0;
        int failCount = 0;
        String lastPassedDate = null;
    }

    private static class TestSummary {
        final String name, lastFailureReason, lastPassDate, status;
        final int passCount, failCount;
        TestSummary(String name, String lastFailureReason, String lastPassDate, String status, int passCount, int failCount) {
            this.name = name;
            this.lastFailureReason = lastFailureReason;
            this.lastPassDate = lastPassDate;
            this.status = status;
            this.passCount = passCount;
            this.failCount = failCount;
        }
    }
}
