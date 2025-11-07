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

public class FlakyTestAnalyzer implements TestExecutionListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile;
    private final List<TestSummary> allTestsList = new ArrayList<>();
    private int totalTests = 0, totalPassed = 0, totalFlaky = 0, totalFailures = 0;

    private final ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    public FlakyTestAnalyzer() {
        this.historyFile = new File("test-history/test-history.json");
        if (!historyFile.getParentFile().exists()) historyFile.getParentFile().mkdirs();

        // Capture console logs
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, consoleBuffer)));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        // Restore console
        System.setOut(originalOut);
        try {
            String logs = consoleBuffer.toString();
            parseFailedScenarios(logs);
            generateHtmlReport();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Parse "Failed scenarios:" and short error messages
    private void parseFailedScenarios(String logs) {
        List<String> failedTests = extractFailedTests(logs);
        Map<String, String> errorMessages = extractErrorMessages(logs);

        for (String testName : failedTests) {
            String reason = errorMessages.getOrDefault(testName, "No detailed error found");
            TestStats stats = getHistoricalStats(testName);
            boolean isFlaky = stats.passCount > 0;

            totalTests++;
            if (isFlaky) {
                totalFlaky++;
                allTestsList.add(new TestSummary(testName, reason, stats.lastPassedDate, "FLAKY"));
            } else {
                totalFailures++;
                allTestsList.add(new TestSummary(testName, reason, stats.lastPassedDate, "FAILED"));
            }

            logToHistory(testName, isFlaky ? "FLAKY" : "FAILED", 0, reason, isFlaky);
        }
    }

    // Extract failed scenario names from "Failed scenarios:" block
    private List<String> extractFailedTests(String logs) {
        List<String> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("Failed scenarios:\\s*(classpath:[^\\n]+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(logs);

        while (matcher.find()) {
            String block = matcher.group(1);
            for (String line : block.split("\\r?\\n")) {
                if (line.trim().startsWith("classpath:")) {
                    result.add(line.trim());
                }
            }
        }

        // Also catch extra classpath lines if printed separately
        Matcher extra = Pattern.compile("classpath:[^\\s]+\\.feature:\\d+").matcher(logs);
        while (extra.find()) {
            String test = extra.group().trim();
            if (!result.contains(test)) result.add(test);
        }
        return result;
    }

    // Extract the first concise error message following each failed test
    private Map<String, String> extractErrorMessages(String logs) {
        Map<String, String> errors = new LinkedHashMap<>();

        // Capture first exception or cause line
        Pattern pattern = Pattern.compile(
                "(classpath:[^\\s]+\\.feature:\\d+)[\\s\\S]{0,300}?(org\\.[a-zA-Z0-9_.]+(?:Exception|Error):[^\n]*)",
                Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(logs);
        while (matcher.find()) {
            String test = matcher.group(1).trim();
            String message = matcher.group(2).trim();
            errors.put(test, message);
        }

        return errors;
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
                .append("th {background:#333; color:#fff;}")
                .append(".PASSED {background:#d4edda;} .FLAKY {background:#fff3cd;} .FAILED {background:#f8d7da;}")
                .append("</style></head><body>");

            html.append("<h1>Test Execution Report</h1>");
            html.append(String.format("<p><b>Total:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</p>",
                    totalTests, totalFlaky, totalFailures));

            html.append("<table>");
            html.append("<tr><th>Test Name</th><th>Status</th><th>Last Passed Date</th><th>Failure Reason</th></tr>");

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
        int passCount = 0, failCount = 0;
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

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
    }
}
