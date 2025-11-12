package com.example.reporting;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ExecutionTimeReporter implements ConcurrentEventListener {

    private final Map<String, Long> startTimes = new HashMap<>();
    private final Map<String, ScenarioResult> results = new LinkedHashMap<>();
    private final String reportFile = "target/cucumber-report.html";

    private long suiteStartTime;
    private long suiteEndTime;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, this::onTestRunStarted);
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, this::onTestRunFinished);
    }

    private void onTestRunStarted(TestRunStarted event) {
        suiteStartTime = System.currentTimeMillis();
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        startTimes.put(event.getTestCase().getName(), System.currentTimeMillis());
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        String name = event.getTestCase().getName();
        Long start = startTimes.get(name);
        if (start != null) {
            long duration = System.currentTimeMillis() - start;
            results.put(name, new ScenarioResult(event.getResult().getStatus().name(), duration));
        }
    }

    private void onTestRunFinished(TestRunFinished event) {
        suiteEndTime = System.currentTimeMillis();
        long totalDuration = suiteEndTime - suiteStartTime;

        String startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(suiteStartTime));
        String endTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(suiteEndTime));

        try (FileWriter fw = new FileWriter(reportFile, true)) {
            fw.write("<hr>");
            fw.write("<h3>🕒 Execution Summary</h3>");
            fw.write("<b>Start Time:</b> " + startTime + "<br>");
            fw.write("<b>End Time:</b> " + endTime + "<br>");
            fw.write("<b>Total Duration:</b> " + formatDuration(totalDuration) + "<br><br>");

            fw.write("<table border='1' cellspacing='0' cellpadding='5'>");
            fw.write("<tr style='background-color:#f2f2f2'><th>Scenario</th><th>Status</th><th>Duration</th></tr>");
            for (Map.Entry<String, ScenarioResult> entry : results.entrySet()) {
                String color = entry.getValue().status.equalsIgnoreCase("PASSED") ? "green" : "red";
                fw.write("<tr><td>" + entry.getKey() + "</td><td style='color:" + color + "'>"
                        + entry.getValue().status + "</td><td>"
                        + formatDuration(entry.getValue().duration) + "</td></tr>");
            }
            fw.write("</table><hr>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long ms = millis % 1000;
        if (minutes > 0)
            return String.format("%dm %ds %dms", minutes, seconds, ms);
        else
            return String.format("%ds %dms", seconds, ms);
    }

    static class ScenarioResult {
        String status;
        long duration;
        ScenarioResult(String status, long duration) {
            this.status = status;
            this.duration = duration;
        }
    }
}
