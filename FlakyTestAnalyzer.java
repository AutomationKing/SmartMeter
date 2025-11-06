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
        // Store outside target so Maven clean doesn’t delete it
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

        logToHistory(testName, status, duration.toMillis(), reason, isFlaky);
        printClassification(testName, status, reason, duration, isFlaky);
    }

    // Detects common flaky failure patterns
    private boolean isFlakyPattern(String message) {
        return message.contains("TimeoutException") ||
               message.contains("NoSuchElementException") ||
               message.contains("StaleElementReferenceException") ||
               message.contains("ElementNotInteractableException") ||
               message.contains("ConnectException") ||
               message.contains("AssertionError") && message.contains("URL");
    }

    private void logToHistory(String testName, String status, long duration, String reason, boolean flakyLike) {
        try {
            ObjectNode root;
            if (historyFile.exists()) {
                root = (ObjectNode) mapper.readTree(historyFile);
            } else {
                root = mapper.createObjectNode();
                root.set("tests", mapper.createObjectNode());
            }

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

    private void printClassification(String testName, String status, String reason, Duration duration, boolean isFlaky) {
        if (status.equals("SUCCESSFUL")) {
            System.out.printf("✅ PASSED: %s (%d ms)%n", testName, duration.toMillis());
        } else if (isFlaky) {
            System.out.printf("⚠️  POSSIBLE FLAKY TEST: %s (%s)%n", testName, reason);
        } else {
            System.out.printf("❌ GENUINE FAILURE: %s (%s)%n", testName, reason);
        }
    }
}
