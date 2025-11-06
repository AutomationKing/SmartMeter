package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestExecutionResult;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

public class FlakyTestAnalyzer implements TestExecutionListener {

    private final Map<String, Instant> startTimes = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile;

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

        String testName = testIdentifier.getDisplayName();
        Duration duration = Duration.between(startTimes.get(testName), Instant.now());
        String status = result.getStatus().toString();
        String reason = result.getThrowable()
                .map(Throwable::toString)
                .orElse("Passed");

        boolean isFlaky = isFlakyPattern(reason);
        TestStats stats = getHistoricalStats(testName);
        String lastPassDate = stats.lastPassedDate;

        logToHistory(testName, status, duration.toMillis(), reason, isFlaky);
        printSummary(testName, status, reason, duration, isFlaky, stats);
    }

    // Detect known flaky indicators
    private boolean isFlakyPattern(String message) {
        return message.contains("TimeoutException") ||
               message.contains("NoSuchElementException") ||
               message.contains("StaleElementReferenceException") ||
               message.contains("ElementNotInteractableException") ||
               message.contains("ConnectException") ||
               (message.contains("AssertionError") && message.contains("URL"));
    }

    private void logToHistory(String testName, String status, long duration, String reason, boolean flakyLike) {
        try {
            ObjectNode root = historyFile.exists()
                    ? (ObjectNode) mapper.readTree(historyFile)
                    : mapper.createObjectNode();

            if (!root.has("tests")) root.set("tests", mapper.createObjectNode());
            ObjectNode testsNode = (ObjectNode) root.get("tests");

            ArrayNode testHistory = testsNode.has(testName)
                    ? (ArrayNode) testsNode.get(testName)
                    : mapper.createArrayNode();

            ObjectNode entry = mapper.createObjectNode();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("status", status);
            entry.put("durationMs", duration);
            entry.put("reason", reason);
            entry.put("flakyPattern", flakyLike);

            testHistory.add(entry);
            testsNode.set(testName, testHistory);
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TestStats getHistoricalStats(String testName) {
        TestStats stats = new TestStats();
        if (!historyFile.exists()) return stats;

        try {
            ObjectNode root = (ObjectNode) mapper.readTree(historyFile);
            ObjectNode testsNode = (ObjectNode) root.get("tests");
            if (testsNode == null || !testsNode.has(testName)) return stats;

            ArrayNode history = (ArrayNode) testsNode.get(testName);
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

    private void printSummary(String testName, String status, String reason, Duration duration,
                              boolean isFlaky, TestStats stats) {

        String statsSummary = String.format(" (History: %d passes / %d fails)", stats.passCount, stats.failCount);

        if (status.equals("SUCCESSFUL")) {
            System.out.printf("✅ PASSED: %s (%d ms)%s%n", testName, duration.toMillis(), statsSummary);
        } else if (isFlaky) {
            System.out.printf("⚠️  POSSIBLE FLAKY TEST: %s%n", testName);
            System.out.printf("   ↪ Reason: %s%n", reason);
            if (stats.lastPassedDate != null)
                System.out.printf("   ↪ Previously PASSED on: %s%n", stats.lastPassedDate);
            System.out.println("   ↪" + statsSummary);
        } else {
            System.out.printf("❌ GENUINE FAILURE: %s%n", testName);
            System.out.printf("   ↪ Reason: %s%n", reason);
            if (stats.lastPassedDate != null)
                System.out.printf("   ↪ Previously PASSED on: %s%n", stats.lastPassedDate);
            System.out.println("   ↪" + statsSummary);
        }
    }

    private static class TestStats {
        int passCount = 0;
        int failCount = 0;
        String lastPassedDate = null;
    }
}
