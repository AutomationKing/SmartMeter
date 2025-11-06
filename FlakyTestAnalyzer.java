package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestExecutionResult;
import org.junit.platform.launcher.TestPlan;

import java.io.*;
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

    private final List<TestSummary> allTestsList = new ArrayList<>();
    private final List<String> capturedConsoleLines = new ArrayList<>();

    // Redirect System.out to capture console logs
    private final PrintStream originalOut = System.out;
    private final PrintStream capturingOut = new PrintStream(new OutputStream() {
        private final StringBuilder sb = new StringBuilder();
        @Override
        public void write(int b) {
            if (b == '\n') {
                capturedConsoleLines.add(sb.toString());
                originalOut.println(sb.toString());
                sb.setLength(0);
            } else {
                sb.append((char) b);
            }
        }
    }, true);

    public FlakyTestAnalyzer() {
        // Capture console output
        System.setOut(capturingOut);

        // Store test history outside target/
        this.historyFile = new File("test-history/test-history.json");
        if (!historyFile.getParentFile().exists()) {
            historyFile.getParentFile().mkdirs();
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            startTimes.put(testIdentifier.getUniqueId(), Instant.now());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (!testIdentifier.isTest()) return;

        // Default test name
        String testKey = testIdentifier.getDisplayName();

        // If failed, try to find feature:line from captured console logs
        if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            for (String line : capturedConsoleLines) {
                if (line.startsWith("Failed scenarioes:") && line.contains(".feature:")) {
                    testKey = line.replace("Failed scenarioes:", "").trim();
                    break;
                }
            }
        }

        // Execution timing
        Instant start = startTimes.getOrDefault(testIdentifier.getUniqueId(), Instant.now());
        Duration duration = Duration.between(start, Instant.now());

        // Exact error message (truncated if long)
        String reason = result.getThrowable()
                .map(Throwable::getMessage)
                .map(msg -> msg != null ? msg : result.getThrowable().get().toString())
                .orElse("Passed");
        if (reason.length() > 200) reason = reason.substring(0, 200) + "...";

        // Historical stats
        TestStats stats = getHistoricalStats(testKey);

        // Flaky detection: known pattern or previously passed at least once
        boolean isFlaky = isFlakyPattern(reason) ||
                          ("FAILED".equals(result.getStatus().toString()) && stats.passCount > 0);

        logToHistory(testKey, result.getStatus().toString(), duration.toMillis(), reason, isFlaky);

        // Update counters & list
        totalTests++;
        if ("SUCCESSFUL".equals(result.getStatus().toString())) {
            totalPassed++;
            allTestsList.add(new TestSummary(testKey, "-", stats.lastPassedDate, "PASSED"));
        } else if (isFlaky) {
            totalFlaky++;
            allTestsList.add(new TestSummary(testKey, reason, stats.lastPassedDate, "FLAKY"));
        } else {
            totalFailures++;
            allTestsList.add(new TestSummary(testKey, reason, stats.lastPassedDate, "FAILED"));
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        generateHtmlReport();
        // Restore original System.out
        System.setOut(originalOut);
    }

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

    private void generateHtmlReport() {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
                .append("<title>Test Report</title>")
                .append("<style>")
                .append("body {font-family: Arial, sans-serif; margin: 20px;}") 
                .append("table {border-collapse: collapse; width: 100%;}")
                .append("th, td {border: 1px solid #ccc; padding: 8px;}") 
                .append("th {background:#555; color:#fff;}") 
                .append(".PASSED {background:#d4edda;}") 
                .append(".FLAKY {background:#fff3cd;}") 
                .append(".FAILED {background:#f8d7da;}") 
                .append("</style></head><body>");

            html.append("<h1>Test Execution Report</h1>");
            html.append(String.format("<p>Total: %d | Passed: %d | Flaky: %d | Failed: %d</p>",
                    totalTests, totalPassed, totalFlaky, totalFailures));

            html.append("<table>");
            html.append("<tr><th>Test Name</th><th>Status</th><th>Last Passed Date</th><th>Last Failure Reason</th></tr>");

            for (TestSummary t : allTestsList) {
                html.append("<tr class='").append(t.status).append("'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>").append(t.status).append("</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("<td>").append(t.lastFailureReason).append("</td>")
                    .append("</tr>");
            }

            html.append("</table></body></html>");

            File reportFile = new File("test-history/test-report.html");
            if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
                writer.write(html.toString());
            }

            System.out.println("✅ HTML Test Report generated at: " + reportFile.getAbsolutePath());

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
        String status;

        TestSummary(String name, String reason, String lastPassDate, String status) {
            this.name = name;
            this.lastFailureReason = reason;
            this.lastPassDate = lastPassDate;
            this.status = status;
        }
    }
}
