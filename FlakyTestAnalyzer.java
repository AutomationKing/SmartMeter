package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestExecutionResult;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

public class FlakyTestAnalyzer implements TestExecutionListener {

    private final Map<String, Instant> startTimes = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile;

    // Counters
    private int totalTests = 0;
    private int totalPassed = 0;
    private int totalFlaky = 0;
    private int totalFailures = 0;

    // Lists for HTML report
    private final List<TestSummary> passedTests = new ArrayList<>();
    private final List<TestSummary> flakyTests = new ArrayList<>();
    private final List<TestSummary> genuineFailures = new ArrayList<>();

    public FlakyTestAnalyzer() {
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

        // Generate consistent test key with feature line for scenario outlines
        String featureLine = testIdentifier.getSource()
                .map(Object::toString)
                .map(src -> {
                    int idx = src.indexOf("feature:");
                    return idx >= 0 ? src.substring(idx) : "";
                }).orElse("");
        String testKey = testIdentifier.getDisplayName() + " " + featureLine;

        Instant start = startTimes.getOrDefault(testIdentifier.getDisplayName(), Instant.now());
        Duration duration = Duration.between(start, Instant.now());

        String status = result.getStatus().toString();
        String reason = result.getThrowable().map(Throwable::toString).orElse("Passed");

        boolean isFlaky = isFlakyPattern(reason);
        TestStats stats = getHistoricalStats(testKey);
        String lastPassDate = stats.lastPassedDate;

        logToHistory(testKey, status, duration.toMillis(), reason, isFlaky);
        printSummary(testKey, status, reason, duration, isFlaky, stats);

        totalTests++;
        if ("SUCCESSFUL".equals(status)) {
            totalPassed++;
            passedTests.add(new TestSummary(testKey, "-", lastPassDate));
        } else if (isFlaky) {
            totalFlaky++;
            flakyTests.add(new TestSummary(testKey, reason, lastPassDate));
        } else {
            totalFailures++;
            genuineFailures.add(new TestSummary(testKey, reason, lastPassDate));
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        generateHtmlReport();
    }

    // Detect common flaky patterns
    private boolean isFlakyPattern(String message) {
        return message.contains("TimeoutException") ||
               message.contains("NoSuchElementException") ||
               message.contains("StaleElementReferenceException") ||
               message.contains("ElementNotInteractableException") ||
               message.contains("ConnectException") ||
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

        } catch (IOException e) {
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
        } catch (IOException e) {
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

    // ✅ Generate HTML report with all tests
    private void generateHtmlReport() {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>Test Execution Report</title>")
                .append("<style>")
                .append("body {font-family: Arial, sans-serif;}")
                .append("table {border-collapse: collapse; width: 100%; margin-bottom: 20px;}")
                .append("th, td {border: 1px solid black; padding: 8px; text-align: left;}")
                .append("th {background-color: #f2f2f2;}")
                .append(".passed {background-color: #d4edda;}")
                .append(".flaky {background-color: #fff3cd;}")
                .append(".failed {background-color: #f8d7da;}")
                .append("</style></head><body>");

            html.append("<h2>Test Execution Summary</h2>");
            html.append(String.format(
                "<p>Total tests executed: %d | Passed: %d | Flaky: %d | Genuine Failures: %d</p>",
                totalTests, totalPassed, totalFlaky, totalFailures
            ));

            html.append("<h3>All Tests</h3><table>");
            html.append("<tr><th>Test Name</th><th>Status</th><th>Last Failure Reason</th><th>Last Passed Date</th></tr>");

            for (TestSummary t : passedTests) {
                html.append("<tr class='passed'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>Passed</td>")
                    .append("<td>-</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("</tr>");
            }
            for (TestSummary t : flakyTests) {
                html.append("<tr class='flaky'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>Flaky</td>")
                    .append("<td>").append(t.lastFailureReason).append("</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("</tr>");
            }
            for (TestSummary t : genuineFailures) {
                html.append("<tr class='failed'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>Failed</td>")
                    .append("<td>").append(t.lastFailureReason).append("</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("</tr>");
            }

            html.append("</table></body></html>");

            File reportFile = new File("test-history/test-report.html");
            if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
            Files.writeString(reportFile.toPath(), html.toString());

            System.out.println("✅ HTML Test Report generated at: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper classes
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
