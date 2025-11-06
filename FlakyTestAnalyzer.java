package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlakyTestAnalyzerConsole implements TestExecutionListener {

    private final File historyFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<TestSummary> testSummaries = new ArrayList<>();
    private final ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    public FlakyTestAnalyzerConsole() {
        this.historyFile = new File("test-history/test-history.json");
        if (!historyFile.getParentFile().exists()) historyFile.getParentFile().mkdirs();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        // Redirect console output to buffer
        System.setOut(new PrintStream(consoleBuffer));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        // Restore original console output
        System.setOut(originalOut);

        String logs = consoleBuffer.toString();
        parseConsoleLogs(logs);
        generateHtmlReport();
    }

    private void parseConsoleLogs(String logs) {
        Pattern failedPattern = Pattern.compile("Failed scenarioes:\\s*(classpath:.*\\.feature:\\d+)");
        Matcher matcher = failedPattern.matcher(logs);

        while (matcher.find()) {
            String testName = matcher.group(1).trim();

            // You can optionally parse the next few lines in logs for exact reason
            String reason = extractFailureReason(logs, testName);

            boolean isFlaky = checkFlaky(reason);
            TestStats stats = getHistoricalStats(testName);

            // Update history
            logToHistory(testName, isFlaky ? "FLAKY" : "FAILED", reason, isFlaky);

            // Add to summary
            testSummaries.add(new TestSummary(testName, reason, stats.lastPassedDate, isFlaky ? "FLAKY" : "FAILED"));
        }
    }

    private String extractFailureReason(String logs, String testName) {
        // Find line after "Failed scenarioes: <testName>" containing Exception
        String[] lines = logs.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(testName) && i + 1 < lines.length) {
                return lines[i + 1].trim();
            }
        }
        return "-";
    }

    private boolean checkFlaky(String reason) {
        return reason.contains("TimeoutException") ||
               reason.contains("NoSuchElementException") ||
               reason.contains("AssertionError") ||
               reason.contains("StaleElementReferenceException") ||
               reason.contains("ConnectException");
    }

    private void logToHistory(String testName, String status, String reason, boolean flakyLike) {
        try {
            ObjectNode root = historyFile.exists()
                    ? (ObjectNode) mapper.readTree(historyFile)
                    : mapper.createObjectNode();

            if (!root.has("tests")) root.set("tests", mapper.createObjectNode());
            ObjectNode testsNode = (ObjectNode) root.get("tests");

            ArrayNode history = testsNode.has(testName)
                    ? (ArrayNode) testsNode.get(testName)
                    : mapper.createArrayNode();

            ObjectNode entry = mapper.createObjectNode();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("status", status);
            entry.put("reason", reason);
            entry.put("flakyPattern", flakyLike);

            history.add(entry);
            testsNode.set(testName, history);
            mapper.writerWithDefaultPrettyPrinter().writeValue(historyFile, root);

        } catch (Exception e) {
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
        } catch (Exception e) { e.printStackTrace(); }
        return stats;
    }

    private void generateHtmlReport() {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><meta charset='UTF-8'><title>Test Report</title>")
                .append("<style>table {border-collapse: collapse;} th,td {border:1px solid #ccc;padding:5px;} ")
                .append(".PASSED {background:#d4edda;} .FLAKY {background:#fff3cd;} .FAILED {background:#f8d7da;}</style>")
                .append("</head><body><h2>Test Report</h2><table>")
                .append("<tr><th>Test Name</th><th>Status</th><th>Last Passed Date</th><th>Failure Reason</th></tr>");

            for (TestSummary t : testSummaries) {
                html.append("<tr class='").append(t.status).append("'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>").append(t.status).append("</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("<td>").append(t.lastFailureReason).append("</td>")
                    .append("</tr>");
            }

            html.append("</table></body></html>");

            File reportFile = new File("test-history/console-log-report.html");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
                writer.write(html.toString());
            }

            System.out.println("✅ HTML Test Report (console logs) generated at: " + reportFile.getAbsolutePath());
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
