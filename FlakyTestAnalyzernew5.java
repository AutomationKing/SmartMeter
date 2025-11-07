private void generateHtmlReport() throws IOException {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
        .append("<title>Test Report</title><style>")
        // General body
        .append("body{font-family:Arial;margin:0;padding:0;background:#f4f6f8;}")
        // Header with image
        .append(".header{background:url('https://www.gov.uk/government/publications/child-maintenance-service-logo/child-maintenance-service.png') no-repeat left center;background-size:80px auto; padding:20px 20px 20px 120px; color:#333;}")
        .append(".header h1{margin:0;font-size:28px;}")
        // Summary stats
        .append(".summary{text-align:center;margin:20px;font-size:16px;}")
        // Table style
        .append("table{border-collapse:collapse;width:95%;margin:20px auto;background:#fff;box-shadow:0 0 10px rgba(0,0,0,0.1);}")
        .append("th,td{border:1px solid #ccc;padding:12px;text-align:left;}")
        .append("th{background:#005ea5;color:#fff;font-size:16px;}")
        .append("tr.PASSED{background:#d4edda;} tr.FLAKY{background:#fff3cd;} tr.FAILED{background:#f8d7da;}")
        .append("tr:hover{background:#e2e6ea;}")
        // Buttons style
        .append(".filters{text-align:center;margin:20px;}")
        .append(".filters button{margin:5px;padding:8px 15px;border:none;border-radius:5px;cursor:pointer;background:#005ea5;color:#fff;font-weight:bold;}")
        .append(".filters button:hover{background:#003d7a;}")
        // Chart container
        .append(".chart-container{width:400px; max-width:50%; height:400px; margin:20px auto;}")
        .append("</style></head><body>");

    // Header
    html.append("<div class='header'><h1>Test Execution Report</h1></div>");

    // Summary stats
    html.append(String.format("<div class='summary'><b>Total:</b> %d | <b>Passed:</b> %d | <b>Flaky:</b> %d | <b>Failed:</b> %d</div>",
            total, passed, flaky, failed));

    // Filters
    html.append("<div class='filters'>")
        .append("<button onclick=\"filter('ALL')\">All</button>")
        .append("<button onclick=\"filter('PASSED')\">Passed</button>")
        .append("<button onclick=\"filter('FLAKY')\">Flaky</button>")
        .append("<button onclick=\"filter('FAILED')\">Failed</button>")
        .append("</div>");

    // Pie chart
    html.append("<div class='chart-container'><canvas id='summaryChart'></canvas></div>")
        .append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>")
        .append("<script>")
        .append("const ctx = document.getElementById('summaryChart').getContext('2d');")
        .append("new Chart(ctx, {type: 'pie',data: {labels: ['Passed', 'Flaky', 'Failed'],datasets: [{")
        .append("data: [").append(passed).append(",").append(flaky).append(",").append(failed).append("],")
        .append("backgroundColor: ['#28a745','#ffc107','#dc3545']}]},options: {responsive: true, maintainAspectRatio: false}});")
        .append("</script>");

    // Table
    html.append("<table><tr><th>Test</th><th>Status</th><th>Last Passed</th><th>Reason</th><th>Pass %</th><th>Trend</th></tr>");
    for (TestSummary s : thisRunSummaries) {
        JsonNode history = testsNode.get(s.name);
        double totalRuns = s.passCount + s.failCount + s.flakyCount;
        double passRate = totalRuns == 0 ? 0 : ((s.passCount + s.flakyCount) * 100.0 / totalRuns);
        String trend = getRecentHistoryTrend(history, 5);
        html.append("<tr class='").append(s.status).append("'>")
            .append("<td>").append(escapeHtml(s.name)).append("</td>")
            .append("<td>").append(s.status).append("</td>")
            .append("<td>").append(s.lastPassDate == null ? "-" : s.lastPassDate).append("</td>")
            .append("<td>").append(escapeHtml(s.lastFailureReason == null ? "-" : s.lastFailureReason)).append("</td>")
            .append("<td>").append(String.format("%.1f%%", passRate)).append("</td>")
            .append("<td>").append(trend).append("</td>")
            .append("</tr>");
    }
    html.append("</table>");

    // Filter script
    html.append("<script>function filter(status){document.querySelectorAll('tr').forEach(tr=>{")
        .append("if(!tr.classList.contains(status)&&status!=='ALL'&&tr.classList.length)tr.style.display='none';")
        .append("else tr.style.display='';});}</script>");

    html.append("</body></html>");

    if (!reportFile.getParentFile().exists()) reportFile.getParentFile().mkdirs();
    Files.writeString(Path.of(reportFile.toURI()), html.toString());
}
