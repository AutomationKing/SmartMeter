private void generateHtmlReport() throws IOException {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
        .append("<title>Test Report</title><style>")
        // Body styling with gradient background
        .append("body{font-family:Arial, sans-serif;margin:20px;background: linear-gradient(to right, #f9f9f9, #e0f7fa);}")
        // Table styling
        .append("table{border-collapse:collapse;width:100%;box-shadow:0 2px 5px rgba(0,0,0,0.1);}")
        .append("th,td{border:1px solid #ccc;padding:10px;text-align:left;}th{background:#00796b;color:white;}") 
        .append(".PASSED{background:#d4edda}.FLAKY{background:#fff3cd}.FAILED{background:#f8d7da}")
        // Button styling
        .append("button{margin-right:5px;padding:5px 10px;border:none;border-radius:4px;cursor:pointer;background:#00796b;color:white;}") 
        .append("button:hover{background:#004d40;}") 
        .append("</style></head><body>");

    // Header image
    html.append("<div style='text-align:center;margin-bottom:20px;'>")
        .append("<img src='https://www.childmaintenance.service/image/logo.png' alt='Child Maintenance Service' style='height:80px;'>")
        .append("</div>");

    // Title and summary
    html.append("<h1 style='text-align:center;color:#00796b;'>Test Execution Report</h1>");
    html.append(String.format("<p style='text-align:center;font-size:16px;'><b>Total:</b> %d | <b>Passed:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</p>",
            total, passed, flaky, failed));

    // Filter buttons
    html.append("<p style='text-align:center;'>")
        .append("<button onclick=\"filter('ALL')\">All</button>")
        .append("<button onclick=\"filter('PASSED')\">Passed</button>")
        .append("<button onclick=\"filter('FLAKY')\">Flaky</button>")
        .append("<button onclick=\"filter('FAILED')\">Failed</button>")
        .append("</p>");

    // Pie chart
    html.append("<div style='width:400px; max-width:50%; height:400px; margin:0 auto 20px;'>")
        .append("<canvas id='summaryChart'></canvas></div>")
        .append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>")
        .append("<script>")
        .append("const ctx = document.getElementById('summaryChart').getContext('2d');")
        .append("new Chart(ctx, {type: 'pie',data: {labels: ['Passed', 'Flaky', 'Failed'],datasets: [{")
        .append("data: [").append(passed).append(",").append(flaky).append(",").append(failed).append("],")
        .append("backgroundColor: ['#28a745','#ffc107','#dc3545']}]},options: {responsive: true, maintainAspectRatio: false}});")
        .append("</script>");

    // Table header
    html.append("<table><tr><th>Test</th><th>Status</th><th>Last Passed</th><th>Reason</th><th>Pass %</th><th>Trend</th></tr>");
    for (TestSummary s : thisRunSummaries) {
        JsonNode history = testsNode.get(s.name);
        double totalRuns = s.passCount + s.failCount + s.flakyCount;
        double passRate = totalRuns == 0 ? 0 : ((s.passCount + s.flakyCount) * 100.0 / totalRuns);
        String trend = getRecentHistoryTrend(history, 5);

        // Fix: include FLAKY class even if test passed now but was flaky before
        String trClass = s.status;
        if (s.flakyCount > 0 && !"FLAKY".equals(s.status)) {
            trClass += " FLAKY";
        }

        html.append("<tr class='").append(trClass).append("'>")
            .append("<td>").append(escapeHtml(s.name)).append("</td>")
            .append("<td>").append(s.status).append("</td>")
            .append("<td>").append(s.lastPassDate == null ? "-" : s.lastPassDate).append("</td>")
            .append("<td>").append(escapeHtml(s.lastFailureReason == null ? "-" : s.lastFailureReason)).append("</td>")
            .append("<td>").append(String.format("%.1f%%", passRate)).append("</td>")
            .append("<td>").append(trend).append("</td>")
            .append("</tr>");
    }
    html.append("</table>");

    // JS filter
    html.append("<script>")
        .append("function filter(status){")
        .append("document.querySelectorAll('tr').forEach(tr=>{")
        .append("if(tr.querySelectorAll('td').length === 0) return;") // skip header row
        .append("if(status === 'ALL'){ tr.style.display=''; }")
        .append("else { tr.style.display = tr.classList.contains(status) ? '' : 'none'; }")
        .append("});")
        .append("}")
        .append("</script>");

    html.append("</body></html>");

    if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
    Files.writeString(Path.of(reportFile.toURI()), html.toString());
}
