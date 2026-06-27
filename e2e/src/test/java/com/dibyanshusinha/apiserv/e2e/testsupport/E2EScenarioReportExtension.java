package com.dibyanshusinha.apiserv.e2e.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class E2EScenarioReportExtension implements BeforeAllCallback, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(E2EScenarioReportExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        E2EReport report = context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(E2EReport.class, key -> new E2EReport(baseUrl()), E2EReport.class);
        report.waitForApplication();
        report.registerOpenApiRoutes();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Optional<Method> testMethod = context.getTestMethod();
        if (testMethod.isEmpty()) {
            return;
        }

        E2EScenario[] scenarios = testMethod.get().getAnnotationsByType(E2EScenario.class);
        if (scenarios.length == 0) {
            return;
        }

        E2EReport report = context.getRoot()
                .getStore(NAMESPACE)
                .get(E2EReport.class, E2EReport.class);
        for (E2EScenario scenario : scenarios) {
            report.registerScenario(scenario, context.getRequiredTestClass().getSimpleName(), testMethod.get().getName(), context.getExecutionException());
        }
    }

    private String baseUrl() {
        return System.getProperty("e2e.base-url", System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080"));
    }

    static final class E2EReport implements ExtensionContext.Store.CloseableResource {

        private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
        private static final String API_CONTRACT_BASE_PATH = "/api-contract/";
        private static final String OPENAPI_CONTRACT_PATH = "/api-contract/openapi.yaml";
        private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        private final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        private final String baseUrl;
        private final Set<ApiRoute> discoveredRoutes = ConcurrentHashMap.newKeySet();
        private final ConcurrentMap<String, List<ScenarioResult>> scenarioResults = new ConcurrentHashMap<>();

        E2EReport(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        void waitForApplication() {
            Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
            while (Instant.now().isBefore(deadline)) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && response.body().contains("UP")) {
                        return;
                    }
                    Thread.sleep(2_000);
                } catch (IOException | InterruptedException ignored) {
                    if (ignored instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        void registerOpenApiRoutes() {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + OPENAPI_CONTRACT_PATH))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }

                JsonNode paths = objectMapper.readTree(response.body()).path("paths");
                Iterator<Map.Entry<String, JsonNode>> fields = paths.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> path = fields.next();
                    Iterator<Map.Entry<String, JsonNode>> operations = resolvePathItem(path.getValue()).fields();
                    while (operations.hasNext()) {
                        Map.Entry<String, JsonNode> operation = operations.next();
                        if (HTTP_METHODS.contains(operation.getKey())) {
                            discoveredRoutes.add(new ApiRoute(serviceName(path.getKey()), operation.getKey().toUpperCase(), path.getKey()));
                        }
                    }
                }
            } catch (IOException ignored) {
                return;
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        private JsonNode resolvePathItem(JsonNode pathItem) throws IOException, InterruptedException {
            JsonNode ref = pathItem.path("$ref");
            if (!ref.isTextual()) {
                return pathItem;
            }

            String refValue = ref.asText();
            String[] refParts = refValue.split("#", 2);
            if (refParts.length == 0 || !refParts[0].startsWith("./")) {
                return pathItem;
            }

            String contractRelativePath = refParts[0].substring(2);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + API_CONTRACT_BASE_PATH + contractRelativePath))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return pathItem;
            }

            JsonNode resolved = objectMapper.readTree(response.body());
            if (refParts.length == 2 && refParts[1].startsWith("/")) {
                for (String segment : refParts[1].substring(1).split("/")) {
                    resolved = resolved.path(segment.replace("~1", "/").replace("~0", "~"));
                }
            }
            return resolved.isMissingNode() ? pathItem : resolved;
        }

        void registerScenario(E2EScenario scenario, String testClass, String testMethod, Optional<Throwable> failure) {
            ApiRoute route = new ApiRoute(scenario.service(), scenario.method(), scenario.route());
            discoveredRoutes.add(route);
            scenarioResults.computeIfAbsent(route.key(), ignored -> new ArrayList<>())
                    .add(new ScenarioResult(
                            scenario.workflow(),
                            scenario.response(),
                            testClass + "." + testMethod,
                            failure.isEmpty() ? ScenarioStatus.PASSED : ScenarioStatus.FAILED,
                            failure.map(this::failureSummary).orElse("")
                    ));
        }

        @Override
        public void close() throws IOException {
            Path reportPath = reportPath();
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, toHtml(buildReportData()), StandardCharsets.UTF_8);
        }

        private Path reportPath() {
            String configuredPath = System.getProperty(
                    "e2e.report.path",
                    System.getenv().getOrDefault("E2E_REPORT_PATH", Path.of("target", "reports", "e2e-scenario-report", "index.html").toString())
            );
            return Path.of(configuredPath);
        }

        private ReportData buildReportData() {
            List<ApiRoute> routes = discoveredRoutes.stream()
                    .sorted(Comparator.comparing(ApiRoute::service).thenComparing(ApiRoute::route).thenComparing(ApiRoute::method))
                    .toList();
            Map<String, List<ApiRoute>> routesByService = routes.stream()
                    .collect(Collectors.groupingBy(ApiRoute::service, java.util.TreeMap::new, Collectors.toList()));
            List<ApiRoute> untestedRoutes = routes.stream()
                    .filter(route -> scenarioResults.getOrDefault(route.key(), List.of()).stream().noneMatch(ScenarioResult::passed))
                    .toList();
            int totalScenarios = scenarioResults.values().stream().mapToInt(List::size).sum();
            int failedScenarios = scenarioResults.values().stream()
                    .flatMap(List::stream)
                    .mapToInt(result -> result.failed() ? 1 : 0)
                    .sum();
            int coveredRoutes = routes.size() - untestedRoutes.size();
            return new ReportData(Instant.now(), routes, routesByService, untestedRoutes, coveredRoutes, totalScenarios, failedScenarios);
        }

        private String toHtml(ReportData data) {
            int routeCount = data.routes().size();
            int untestedRoutes = data.untestedRoutes().size();
            int passedScenarios = data.totalScenarios() - data.failedScenarios();
            int coveredPercent = routeCount == 0 ? 100 : (int) Math.round((data.coveredRoutes() * 100.0) / routeCount);
            String overallStatus = data.failedScenarios() == 0 && untestedRoutes == 0 ? "PASSED" : "ATTENTION";
            String overallClass = "PASSED".equals(overallStatus) ? "passed" : "attention";

            StringBuilder html = new StringBuilder();
            html.append("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>E2E Scenario Report</title>
                      <style>
                        :root { --border: #d8dee4; --muted: #57606a; --bg: #f6f8fa; --ok: #1a7f37; --bad: #cf222e; --warn: #9a6700; }
                        * { box-sizing: border-box; }
                        body { margin: 0; background: #fff; color: #24292f; font-family: Arial, Helvetica, sans-serif; font-size: 14px; }
                        .breadcrumb, .footer { background: var(--bg); border-bottom: 1px solid var(--border); padding: 10px 18px; color: var(--muted); }
                        .footer { border-top: 1px solid var(--border); border-bottom: 0; text-align: right; }
                        main { padding: 22px 28px 32px; max-width: 1280px; }
                        h1 { margin: 0; font-size: 24px; font-weight: 600; }
                        h2 { margin: 26px 0 12px; font-size: 18px; font-weight: 600; }
                        .meta { color: var(--muted); margin-top: 8px; line-height: 1.5; }
                        .hero { border: 1px solid var(--border); border-radius: 6px; padding: 18px; margin: 18px 0 20px; background: linear-gradient(180deg, #fff, #f8fafc); }
                        .hero-top { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
                        .overall { font-size: 28px; font-weight: 700; line-height: 1; }
                        .passed { color: var(--ok); }
                        .failed, .missing { color: var(--bad); }
                        .attention { color: var(--warn); }
                        .summary-grid { display: grid; grid-template-columns: repeat(5, minmax(130px, 1fr)); gap: 10px; }
                        .metric { border: 1px solid var(--border); border-radius: 6px; padding: 12px; background: #fff; }
                        .metric-label { color: var(--muted); font-size: 12px; font-weight: 700; text-transform: uppercase; }
                        .metric-value { display: block; margin-top: 6px; font-size: 22px; font-weight: 700; }
                        .bar { height: 10px; border: 1px solid #c8c8c8; border-radius: 999px; background: #ffebe9; overflow: hidden; margin-top: 10px; }
                        .bar span { display: block; height: 100%; background: #3fb950; }
                        .tree { border: 1px solid var(--border); border-radius: 6px; margin-bottom: 18px; background: #fff; overflow: hidden; }
                        details.tree-node { border-top: 1px solid var(--border); }
                        details.tree-node:first-child { border-top: 0; }
                        details.tree-node[open] > summary.tree-summary { background: #f8fafc; }
                        summary.tree-summary { cursor: pointer; display: grid; grid-template-columns: 28px 1fr auto; gap: 10px; align-items: center; padding: 10px 14px; list-style: none; }
                        summary.tree-summary:hover, summary.route-summary:hover { background: #f3f6f9; }
                        summary.tree-summary::-webkit-details-marker, summary.route-summary::-webkit-details-marker { display: none; }
                        summary.tree-summary::before, summary.route-summary::before { content: "›"; width: 22px; height: 22px; border: 1px solid var(--border); border-radius: 4px; color: var(--muted); background: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; line-height: 1; transition: transform 0.12s ease, background 0.12s ease; }
                        details[open] > summary.tree-summary::before, details[open] summary.route-summary::before { transform: rotate(90deg); background: #eef4ff; color: #0969da; }
                        .tree-label { display: inline-flex; gap: 8px; align-items: baseline; font-weight: 700; }
                        .tree-counts { color: var(--muted); font-size: 13px; }
                        .tree-children { margin-left: 22px; border-left: 1px solid var(--border); padding: 6px 10px 10px; }
                        details.route-card { border: 1px solid var(--border); border-radius: 6px; margin: 8px 0; background: #fff; }
                        summary.route-summary { cursor: pointer; display: grid; grid-template-columns: 28px 90px 1fr 140px 150px; gap: 10px; align-items: center; padding: 10px 12px; list-style: none; }
                        .method, .path, .test, .failure { font-family: Menlo, Consolas, monospace; }
                        .method { font-weight: 700; }
                        .badge { display: inline-block; border-radius: 999px; padding: 3px 8px; background: #dafbe1; color: var(--ok); font-size: 12px; font-weight: 700; text-align: center; }
                        .badge.failed { background: #ffebe9; color: var(--bad); }
                        .badge.warning { background: #fff8c5; color: var(--warn); }
                        .route-body { border-top: 1px solid var(--border); padding: 12px; background: #fbfcfd; }
                        table.scenarios { border-collapse: collapse; width: 100%; background: #fff; }
                        table.scenarios th, table.scenarios td { border: 1px solid var(--border); padding: 8px 9px; vertical-align: top; }
                        table.scenarios th { background: var(--bg); text-align: left; font-size: 12px; text-transform: uppercase; color: var(--muted); }
                        .failure { color: var(--bad); font-size: 12px; }
                        .empty { color: var(--muted); padding: 10px; }
                      </style>
                    </head>
                    <body>
                      <div class="breadcrumb"><strong>E2E Scenario Report</strong></div>
                      <main>
                    """);
            html.append("<h1>E2E Scenario Report</h1><div class=\"meta\">Generated by <code>mvn -f e2e/pom.xml verify</code> at <code>")
                    .append(escape(data.generatedAt().toString()))
                    .append("</code>. Routes are discovered from <code>")
                    .append(escape(baseUrl))
                    .append(OPENAPI_CONTRACT_PATH)
                    .append("</code>. E2E scenarios are workflow checks against the running service.</div>");
            html.append("<section class=\"hero\"><div class=\"hero-top\"><div><div class=\"overall ")
                    .append(overallClass)
                    .append("\">")
                    .append(overallStatus)
                    .append("</div><div class=\"meta\">Production-like workflow execution result</div></div><div><span class=\"passed\">")
                    .append(coveredPercent)
                    .append("% route workflow coverage</span><div class=\"bar\"><span style=\"width:")
                    .append(coveredPercent)
                    .append("%\"></span></div></div></div><div class=\"summary-grid\">")
                    .append(metric("Discovered routes", String.valueOf(routeCount), ""))
                    .append(metric("Covered routes", data.coveredRoutes() + " of " + routeCount, untestedRoutes == 0 ? "passed" : "attention"))
                    .append(metric("Scenarios", String.valueOf(data.totalScenarios()), ""))
                    .append(metric("Passed", String.valueOf(passedScenarios), "passed"))
                    .append(metric("Failed / Untested", data.failedScenarios() + " / " + untestedRoutes, data.failedScenarios() == 0 && untestedRoutes == 0 ? "passed" : "failed"))
                    .append("</div></section>");
            data.routesByService().forEach((service, routes) -> appendService(html, service, routes));
            appendUntestedRoutes(html, data.untestedRoutes());
            html.append("</main><div class=\"footer\">Generated by E2E scenario report extension</div></body></html>");
            return html.toString();
        }

        private void appendService(StringBuilder html, String service, List<ApiRoute> routes) {
            int scenarios = routes.stream().mapToInt(route -> scenarioResults.getOrDefault(route.key(), List.of()).size()).sum();
            Map<String, List<ApiRoute>> routesByVersion = routes.stream()
                    .collect(Collectors.groupingBy(route -> apiVersion(route.route()), java.util.TreeMap::new, Collectors.toList()));
            html.append("<section class=\"tree\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">E2ETests</span><span class=\"tree-counts\">API calls: ")
                    .append(routes.size())
                    .append(" | Scenarios: ")
                    .append(scenarios)
                    .append("</span></summary><div class=\"tree-children\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">API</span><span class=\"tree-counts\">")
                    .append(routes.size())
                    .append(" calls</span></summary><div class=\"tree-children\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">")
                    .append(escape(service))
                    .append("</span><span class=\"tree-counts\">")
                    .append(scenarios)
                    .append(" scenarios</span></summary><div class=\"tree-children\">");
            routesByVersion.forEach((version, versionRoutes) -> {
                html.append("<details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">")
                        .append(escape(version))
                        .append("</span><span class=\"tree-counts\">")
                        .append(versionRoutes.size())
                        .append(" calls</span></summary><div class=\"tree-children\">");
                versionRoutes.forEach(route -> appendRoute(html, route));
                html.append("</div></details>");
            });
            html.append("</div></details></div></details></div></details></section>");
        }

        private void appendRoute(StringBuilder html, ApiRoute route) {
            List<ScenarioResult> scenarios = scenarioResults.getOrDefault(route.key(), List.of()).stream()
                    .sorted(Comparator.comparing(ScenarioResult::workflow).thenComparing(ScenarioResult::response))
                    .toList();
            long failed = scenarios.stream().filter(ScenarioResult::failed).count();
            long passed = scenarios.stream().filter(ScenarioResult::passed).count();
            String badgeClass = scenarios.isEmpty() || passed == 0 ? "warning" : failed > 0 ? "failed" : "";
            String badgeText = scenarios.isEmpty() || passed == 0 ? "UNTESTED" : failed > 0 ? "FAILED" : "PASSED";
            html.append("<details class=\"route-card\"><summary class=\"route-summary\"><span class=\"method\">")
                    .append(escape(route.method()))
                    .append("</span><span class=\"path\">")
                    .append(escape(route.route()))
                    .append("</span><span><span class=\"badge")
                    .append(badgeClass.isBlank() ? "" : " " + badgeClass)
                    .append("\">")
                    .append(badgeText)
                    .append("</span></span><span>")
                    .append(passed)
                    .append(" passed / ")
                    .append(failed)
                    .append(" failed</span></summary><div class=\"route-body\">");
            if (scenarios.isEmpty()) {
                html.append("<div class=\"empty\">No E2E scenario is annotated for this route.</div>");
            } else {
                html.append("<table class=\"scenarios\"><thead><tr><th>Status</th><th>Workflow</th><th>Response</th><th>Test</th><th>Failure</th></tr></thead><tbody>");
                scenarios.forEach(scenario -> html.append("<tr><td><span class=\"badge")
                        .append(scenario.failed() ? " failed\">FAILED" : "\">PASSED")
                        .append("</span></td><td>")
                        .append(escape(scenario.workflow()))
                        .append("</td><td>")
                        .append(escape(scenario.response()))
                        .append("</td><td class=\"test\">")
                        .append(escape(scenario.test()))
                        .append("</td><td class=\"failure\">")
                        .append(escape(scenario.failure()))
                        .append("</td></tr>"));
                html.append("</tbody></table>");
            }
            html.append("</div></details>");
        }

        private void appendUntestedRoutes(StringBuilder html, List<ApiRoute> untestedRoutes) {
            html.append("<h2>Untested Route Coverage</h2>");
            if (untestedRoutes.isEmpty()) {
                html.append("<section class=\"tree\"><div class=\"empty\"><span class=\"badge\">PASSED</span> No discovered API routes are untested.</div></section>");
                return;
            }
            html.append("<section class=\"tree\"><div class=\"tree-children\">");
            untestedRoutes.forEach(route -> html.append("<div class=\"empty\"><span class=\"badge warning\">UNTESTED</span> ")
                    .append(escape(route.service()))
                    .append(" / <span class=\"method\">")
                    .append(escape(route.method()))
                    .append("</span> <span class=\"path\">")
                    .append(escape(route.route()))
                    .append("</span></div>"));
            html.append("</div></section>");
        }

        private String metric(String label, String value, String valueClass) {
            return "<div class=\"metric\"><span class=\"metric-label\">" + escape(label)
                    + "</span><span class=\"metric-value " + escape(valueClass) + "\">"
                    + escape(value) + "</span></div>";
        }

        private String apiVersion(String route) {
            String[] parts = route.split("/");
            if (parts.length > 2 && "api".equals(parts[1])) {
                return parts[2];
            }
            return "unversioned";
        }

        private String serviceName(String route) {
            String[] parts = route.split("/");
            if (parts.length > 3 && "api".equals(parts[1])) {
                return singularTitle(parts[3]);
            }
            return "API";
        }

        private String singularTitle(String value) {
            String normalized = value.endsWith("s") && value.length() > 1 ? value.substring(0, value.length() - 1) : value;
            return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
        }

        private String failureSummary(Throwable failure) {
            String message = failure.getMessage();
            if (message == null || message.isBlank()) {
                return failure.getClass().getSimpleName();
            }
            return failure.getClass().getSimpleName() + ": " + message.lines().findFirst().orElse("");
        }

        private String escape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }

    record ApiRoute(String service, String method, String route) {
        String key() {
            return method + " " + route;
        }
    }

    record ScenarioResult(String workflow, String response, String test, ScenarioStatus status, String failure) {
        boolean passed() {
            return status == ScenarioStatus.PASSED;
        }

        boolean failed() {
            return status == ScenarioStatus.FAILED;
        }
    }

    enum ScenarioStatus {
        PASSED,
        FAILED
    }

    record ReportData(
            Instant generatedAt,
            List<ApiRoute> routes,
            Map<String, List<ApiRoute>> routesByService,
            List<ApiRoute> untestedRoutes,
            int coveredRoutes,
            int totalScenarios,
            int failedScenarios
    ) {
    }
}
