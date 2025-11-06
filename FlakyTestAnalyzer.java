package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestExecutionResult;
import org.junit.platform.launcher.TestPlan;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

public class FlakyTestAnalyzer implements TestExecutionListener {

    private final Map<String, Instant> startTimes = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile;

    private int totalTests = 0;
    private int totalPassed = 0;
    private int totalFlaky = 0;
    private int totalFailures = 0;

    private final List<TestSummary> passedTests = new ArrayList<>();
    private final List<TestSummary> flakyTests = new ArrayList<>();
    private final List<TestSummary> genuineFailures = new ArrayList<>();

    public FlakyTestAnalyzer() {
        // Store test history outside target/
        this.historyFile = new File("test-history/test-history.json");
        if (!historyFile.getParentFile().exists()) {
            historyFile.getParentFile().mkdirs();
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            startTimes.put(testIdentifier.getDisplayName(), Instant.now());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) return;

        // ✅ Generate a unique key using displayName + feature line + example
        String featureLine = testIdentifier.getSource()
                .map(Object::toString)
                .map(src -> {
                    int idx = src.indexOf("feature:");
                    return idx >= 0 ? src.substring(idx) : "";
                }).orElse("");

        String testKey = testIdentifier.getDisplayName() + " " + featureLine;

        String displayName = testIdentifier.getDisplayName();
        if(displayName.contains("[")) {
            int start = displayName.indexOf("[");
            int end = displayName.indexOf("]", start);
            if (start >= 0 && end > start) {
                testKey += " " + displayName.substring(start, end + 1);
            }
        }

        Instant start = startTimes.getOrDefault(testIdentifier.getDisplayName(), Instant.now());
        Duration duration = Duration.between(start, Instant.now());

        // ✅ Only the exact error message, truncated if too long
        String reason = result.getThrowable()
                .map(Throwable::getMessage)
                .map(msg -> msg != null ? msg : result.getThrowable().get().toString())
                .orElse("Passed");
        int maxLength = 200;
        reason = reason.length() > maxLength ? reason.substring(0, maxLength) + "..." : reason;

        TestStats stats = getHistoricalStats(testKey);

        // ✅ Mark as flaky if pattern matches OR previously passed at least once
        boolean isFlaky = isFlakyPattern(reason) || ("FAILED".equals(result.getStatus().toString()) && stats.passCount > 0);

        logToHistory(testKey, result.getStatus().toString(), duration.toMillis(), reason, isFlaky);
        printSummary(testKey, result.getStatus().toString(), reason, duration, isFlaky, stats);

        totalTests++;
        if ("SUCCESSFUL".equals(result.getStatus().toString())) {
            totalPassed++;
            passedTests.add(new TestSummary(testKey, "-", stats.lastPassedDate));
        } else if (isFlaky) {
            totalFlaky++;
            flakyTests.add(new TestSummary(testKey, reason, stats.lastPassedDate));
        } else {
            totalFailures++;
            genuineFailures.add(new TestSummary(testKey, reason, stats.lastPassedDate));
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        generateDynamicHtmlReport();
    }

    // Detect known flaky indicators
    private boolean isFlakyPattern(String message) {
        return message.contains("TimeoutException") ||
               message.contains("NoSuchElementException") ||
               message.contains("StaleElementReferenceException") ||
               message.contains("ElementNotInteractableException") ||
               message.contains("ConnectException") ||
               message.contains("ElementNotVisibleException") ||
               (message.contains("AssertionError") && message.contains("URL"));
    }

    private void logToHistory(String testKey, String status, long duration, String reason, boolean flakyLike) {
        try {
            ObjectNode root = historyFile.exists()
                    ? (ObjectNode) mapper.readTree(historyFile)
                    : mapper.createObjectNode();

            if (!root.has("tests")) root.set("tests", mapper.createObjectNode());
            ObjectNode testsNode = (ObjectNode) root.get("tests");

            ArrayNode testHistory = testsNode.has(testKey)
                    ? (ArrayNode) testsNode.get(testKey)
                    : mapper.createArrayNode();

            ObjectNode entry = mapper.createObjectNode();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("status", status);
            entry.put("durationMs", duration);
            entry.put("reason", reason);
            entry.put("flakyPattern", flakyLike);

            testHistory.add(entry);
            testsNode.set(testKey, testHistory);
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, root);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TestStats getHistoricalStats(String testKey) {
        TestStats stats = new TestStats();
        if (!historyFile.exists()) return stats;

        try {
            ObjectNode root = (ObjectNode) mapper.readTree(historyFile);
            ObjectNode testsNode = (ObjectNode) root.get("tests");
            if (testsNode == null || !testsNode.has(testKey)) return stats;

            ArrayNode history = (ArrayNode) testsNode.get(testKey);
            for (int i = 0; i < history.size(); i++) {
                ObjectNode entry = (ObjectNode) history.get(i);
                String status = entry.get("status").asText();
                if ("SUCCESSFUL".equals(status)) {
                    stats.passCount++;
                    stats.lastPassedDate = entry.get("timestamp").asText();
                } else {
                    stats.failCount++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }

    private void printSummary(String testKey, String status, String reason, Duration duration,
                              boolean isFlaky, TestStats stats) {
        String statsSummary = String.format(" (History: %d passes / %d fails)", stats.passCount, stats.failCount);

        if ("SUCCESSFUL".equals(status)) {
            System.out.printf("✅ PASSED: %s (%d ms)%s%n", testKey, duration.toMillis(), statsSummary);
        } else if (isFlaky) {
            System.out.printf("⚠️  POSSIBLE FLAKY TEST: %s%n   ↪ Reason: %s%n", testKey, reason);
            if (stats.lastPassedDate != null) System.out.printf("   ↪ Previously PASSED on: %s%n", stats.lastPassedDate);
            System.out.println("   ↪" + statsSummary);
        } else {
            System.out.printf("❌ GENUINE FAILURE: %s%n   ↪ Reason: %s%n", testKey, reason);
            if (stats.lastPassedDate != null) System.out.printf("   ↪ Previously PASSED on: %s%n", stats.lastPassedDate);
            System.out.println("   ↪" + statsSummary);
        }
    }

    // ✅ Generate dynamic HTML report
    private void generateDynamicHtmlReport() {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
                .append("<title>Dynamic Test Report</title>")
                .append("<style>")
                .append("body {font-family: Arial, sans-serif; margin: 20px; background: #f4f4f9;}")
                .append("h1,h2 {color:#333;}")
                .append(".summary {margin-bottom:20px;}")
                .append("table {border-collapse: collapse; width: 100%;}")
                .append("th, td {border: 1px solid #ccc; padding: 8px; text-align:left;}")
                .append("th {background:#555; color:#fff;}")
                .append(".passed {background:#d4edda;}")
                .append(".flaky {background:#fff3cd;}")
                .append(".failed {background:#f8d7da;}")
                .append(".details {display:none; padding: 10px; margin-top:5px; border-left:3px solid #333; background:#eee;}")
                .append(".clickable {cursor:pointer;}")
                .append("</style>")
                .append("<script>")
                .append("function toggleDetail(id) {")
                .append("  var e=document.getElementById(id);")
                .append("  e.style.display = (e.style.display==='none') ? 'block':'none';")
                .append("}")
                .append("</script></head><body>");

            html.append("<h1>Dynamic Test Execution Report</h1>");
            html.append(String.format("<div class='summary'><b>Total:</b> %d | <b>Passed:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</div>",
                    totalTests, totalPassed, totalFlaky, totalFailures));

            html.append("<table>");
            html.append("<tr><th>Test Name</th><th>Status</th><th>Last Passed Date</th></tr>");

            int idCounter = 0;
            List<TestSummary> allTests = new ArrayList<>();
            allTests.addAll(passedTests);
            allTests.addAll(flakyTests);
            allTests.addAll(genuineFailures);

            for (TestSummary t : allTests) {
                String cssClass = t.lastFailureReason.equals("-") ? "passed" :
                        flakyTests.contains(t) ? "flaky" : "failed";

                String detailId = "detail" + (idCounter++);
                html.append("<tr class='clickable ").append(cssClass).append("' onclick=\"toggleDetail('").append(detailId).append("')\">")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>").append(cssClass.toUpperCase()).append("</td>")
                    .append("<td>").append(t.lastPassDate==null?"-":t.lastPassDate).append("</td>")
                    .append("</tr>");

                html.append("<tr id='").append(detailId).append("' class='details'><td colspan='3'>")
                    .append("<b>Last Failure Reason:</b> ").append(t.lastFailureReason)
                    .append("</td></tr>");
            }

            html.append("</table></body></html>");

            File reportFile = new File("test-history/dynamic-test-report.html");
            if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
                writer.write(html.toString());
            }

            System.out.println("✅ Dynamic HTML Test Report generated at: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TestStats {
        int passCount = 0;
        int failCount = 0;
        String lastPassedDate = null;
    }

    private static class TestSummary {
        String name;
        String lastFailureReason;
        String lastPassDate;

        TestSummary(String name, String reason, String lastPassDate) {
            this.name = name;
            this.lastFailureReason = reason;
            this.lastPassDate = lastPassDate;
        }
    }
}
