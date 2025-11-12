package hooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;

public class ReportMerger {

    public static void mergeAllReports() throws IOException {
        Path targetDir = Paths.get("target");
        Path cucumber = targetDir.resolve("cucumber-report.html");
        Path summary = targetDir.resolve("execution-summary.html");
        Path testReport = targetDir.resolve("test-history/test-report.html");
        Path finalReport = targetDir.resolve("final-report.html");

        StringBuilder merged = new StringBuilder();

        merged.append("<html><head><title>Final Combined Report</title>")
              .append("<style>")
              .append("body { font-family: Arial, sans-serif; margin: 30px; background-color: #fafafa; }")
              .append("h2 { background: #222; color: white; padding: 10px; border-radius: 5px; }")
              .append("hr { border: none; border-top: 2px solid #ccc; margin: 40px 0; }")
              .append("iframe { width: 100%; height: 800px; border: 1px solid #ccc; border-radius: 8px; }")
              .append("</style></head><body>")
              .append("<h1>📊 Unified Automation Report</h1>")
              .append("<p>Generated on: ").append(LocalDateTime.now().toString()).append("</p><hr>");

        if (Files.exists(cucumber)) {
            merged.append("<h2>🐞 Cucumber Detailed Report</h2>")
                  .append("<iframe src='cucumber-report.html'></iframe>");
        } else {
            merged.append("<p>⚠️ Cucumber report not found.</p>");
        }

        if (Files.exists(summary)) {
            merged.append("<hr><h2>🕒 Execution Summary</h2>")
                  .append(readFile(summary));
        } else {
            merged.append("<p>⚠️ Execution summary not found.</p>");
        }

        if (Files.exists(testReport)) {
            merged.append("<hr><h2>📋 Custom Test Report</h2>")
                  .append(readFile(testReport));
        } else {
            merged.append("<p>⚠️ Test report not found in test-history folder.</p>");
        }

        merged.append("</body></html>");

        Files.write(finalReport, merged.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("✅ Final combined report generated at: " + finalReport.toAbsolutePath());
    }

    private static String readFile(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
