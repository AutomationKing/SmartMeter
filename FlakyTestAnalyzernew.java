package utils;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlakyTestAnalyzer implements TestExecutionListener {

    private final ObjectMapper mapper = new ObjectMapper();
    private final File historyFile;
    private final List<TestSummary> allTestsList = new ArrayList<>();

    private int totalTests = 0;
    private int totalFlaky = 0;
    private int totalFailures = 0;

    private final ByteArrayOutputStream consoleBuffer = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    public FlakyTestAnalyzer() {
        this.historyFile = new File("test-history/test-history.json");
        if (!historyFile.getParentFile().exists()) historyFile.getParentFile().mkdirs();
        System.setOut(new PrintStream(new TeeOutputStream(originalOut, consoleBuffer)));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.setOut(originalOut);
        try {
            String logs = consoleBuffer.toString();
            parseFailedScenarios(logs);
            generateHtmlReport();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseFailedScenarios(String logs) {
        Pattern pattern = Pattern.compile("classpath:[^\\s]+\\.feature:\\d+");
        Matcher matcher = pattern.matcher(logs);

        Set<String> failedTests = new LinkedHashSet<>();
        while (matcher.find()) {
            failedTests.add(matcher.group());
        }

        // Mark all tests that ever existed in history
        Set<String> allTests = new LinkedHashSet<>(failedTests);
        ObjectNode root = readHistory();
        if (root.has("tests")) {
            allTests.addAll(((ObjectNode) root.get("tests")).fieldNames().toSet());
        }

        for (String testName : allTests) {
            boolean failedNow = failedTests.contains(testName);
            TestStats stats = getHistoricalStats(testName);
            boolean flaky = stats.passCount > 0 && failedNow;

            totalTests++;
            if (failedNow) {
                if (flaky) totalFlaky++;
                else totalFailures++;
                logToHistory(testName, "FAILED", "Failed this run");
            } else {
                logToHistory(testName, "SUCCESSFUL", "Passed this run");
            }

            // Refresh stats after writing
            TestStats updated = getHistoricalStats(testName);
            double stability = updated.totalRuns == 0 ? 0 : (updated.passCount * 100.0 / updated.totalRuns);
            String historySymbols = updated.lastResults;

            String status = failedNow ? (flaky ? "FLAKY" : "FAILED") : "PASSED";
            allTestsList.add(new TestSummary(testName, status, updated.lastPassedDate, historySymbols, stability));
        }
    }

    private ObjectNode readHistory() {
        try {
            return historyFile.exists() ? (ObjectNode) mapper.readTree(historyFile) : mapper.createObjectNode();
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private void logToHistory(String testKey, String status, String reason) {
        try {
            ObjectNode root = readHistory();
            if (!root.has("tests")) root.set("tests", mapper.createObjectNode());
            ObjectNode testsNode = (ObjectNode) root.get("tests");

            ArrayNode testHistory = testsNode.has(testKey) ?
                    (ArrayNode) testsNode.get(testKey) : mapper.createArrayNode();

            ObjectNode entry = mapper.createObjectNode();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("status", status);
            entry.put("reason", reason);
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
            stats.totalRuns = history.size();
            StringBuilder lastResults = new StringBuilder();

            for (int i = Math.max(0, history.size() - 5); i < history.size(); i++) {
                String status = history.get(i).get("status").asText();
                lastResults.append("SUCCESSFUL".equals(status) ? "✅" : "❌");
                if ("SUCCESSFUL".equals(status)) {
                    stats.passCount++;
                    stats.lastPassedDate = history.get(i).get("timestamp").asText();
                } else {
                    stats.failCount++;
                }
            }
            stats.lastResults = lastResults.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }

    private void generateHtmlReport() {
        try {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<title>Flaky Test Analyzer</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:20px;}")
                .append("table{border-collapse:collapse;width:100%;}")
                .append("th,td{border:1px solid #ccc;padding:8px;text-align:left;}")
                .append("th{background:#555;color:#fff;cursor:pointer;}")
                .append(".PASSED{background:#d4edda;} .FLAKY{background:#fff3cd;} .FAILED{background:#f8d7da;}")
                .append(".bar{height:12px;border-radius:4px;background:#eee;}")
                .append(".fill{height:12px;border-radius:4px;background:#28a745;}")
                .append("</style>")
                .append("<script>")
                .append("function sortTable(n){var table=document.getElementById('testTable');var rows=table.rows;var switching=true;var dir='asc';var switchcount=0;while(switching){switching=false;for(var i=1;i<(rows.length-1);i++){var shouldSwitch=false;var x=rows[i].getElementsByTagName('TD')[n];var y=rows[i+1].getElementsByTagName('TD')[n];if(dir=='asc'){if(x.innerHTML.toLowerCase()>y.innerHTML.toLowerCase()){shouldSwitch=true;break;}}else if(dir=='desc'){if(x.innerHTML.toLowerCase()<y.innerHTML.toLowerCase()){shouldSwitch=true;break;}}}if(shouldSwitch){rows[i].parentNode.insertBefore(rows[i+1],rows[i]);switching=true;switchcount++;}else{if(switchcount==0&&dir=='asc'){dir='desc';switching=true;}}}}")
                .append("</script></head><body>");

            html.append("<h1>Flaky Test Analyzer Report</h1>");
            html.append(String.format("<p>Generated: %s</p>", LocalDateTime.now()));
            html.append(String.format("<p>Total Tests: %d | Flaky: %d | Failed: %d</p>", totalTests, totalFlaky, totalFailures));

            html.append("<table id='testTable'>");
            html.append("<tr><th onclick='sortTable(0)'>Test</th><th onclick='sortTable(1)'>Status</th><th onclick='sortTable(2)'>Stability %</th><th onclick='sortTable(3)'>Last 5 Runs</th><th onclick='sortTable(4)'>Last Passed Date</th></tr>");

            for (TestSummary t : allTestsList) {
                html.append("<tr class='").append(t.status).append("'>")
                    .append("<td>").append(t.name).append("</td>")
                    .append("<td>").append(t.status).append("</td>")
                    .append("<td>")
                    .append(String.format("<div class='bar'><div class='fill' style='width:%.1f%%'></div></div> %.1f%%", t.stability, t.stability))
                    .append("</td>")
                    .append("<td>").append(t.lastResults).append("</td>")
                    .append("<td>").append(t.lastPassDate == null ? "-" : t.lastPassDate).append("</td>")
                    .append("</tr>");
            }
            html.append("</table></body></html>");

            File reportFile = new File("test-history/test-report.html");
            Files.writeString(Path.of(reportFile.toURI()), html.toString());
            System.out.println("✅ Enhanced HTML Test Report generated at: " + reportFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TestStats {
        int passCount = 0, failCount = 0, totalRuns = 0;
        String lastPassedDate = null;
        String lastResults = "";
    }

    private static class TestSummary {
        String name, status, lastPassDate, lastResults;
        double stability;
        TestSummary(String name, String status, String lastPassDate, String lastResults, double stability) {
            this.name = name;
            this.status = status;
            this.lastPassDate = lastPassDate;
            this.lastResults = lastResults;
            this.stability = stability;
        }
    }

    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1, out2;
        TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1; this.out2 = out2;
        }
        @Override public void write(int b) throws IOException { out1.write(b); out2.write(b); }
    }
}
