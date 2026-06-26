package com.dibyanshusinha.apiserv.testsupport;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class ApiScenarioCoverageExtension implements BeforeAllCallback, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ApiScenarioCoverageExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        ScenarioCoverageReport report = context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(ScenarioCoverageReport.class, key -> new ScenarioCoverageReport(), ScenarioCoverageReport.class);

        RequestMappingHandlerMapping handlerMapping = SpringExtension.getApplicationContext(context)
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        report.registerRoutes(handlerMapping);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Optional<Method> testMethod = context.getTestMethod();
        if (testMethod.isEmpty()) {
            return;
        }

        ApiScenario scenario = testMethod.get().getAnnotation(ApiScenario.class);
        if (scenario == null) {
            return;
        }

        ScenarioCoverageReport report = context.getRoot()
                .getStore(NAMESPACE)
                .get(ScenarioCoverageReport.class, ScenarioCoverageReport.class);
        report.registerScenario(scenario, context.getRequiredTestClass().getSimpleName(), testMethod.get().getName(), context.getExecutionException());
    }

    static final class ScenarioCoverageReport implements ExtensionContext.Store.CloseableResource {

        private static final Path HTML_REPORT_PATH = Path.of("target", "reports", "api-scenario-coverage", "index.html");
        private final Set<ApiRoute> discoveredRoutes = ConcurrentHashMap.newKeySet();
        private final ConcurrentMap<String, List<ScenarioResult>> scenarioResults = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Set<ExpectedResponse>> expectedResponses = new ConcurrentHashMap<>();

        void registerRoutes(RequestMappingHandlerMapping handlerMapping) {
            handlerMapping.getHandlerMethods().forEach(this::registerRoute);
        }

        void registerScenario(ApiScenario scenario, String testClass, String testMethod, Optional<Throwable> failure) {
            ApiRoute route = new ApiRoute(scenario.service(), scenario.method(), scenario.route());
            discoveredRoutes.add(route);
            scenarioResults.computeIfAbsent(route.key(), ignored -> new ArrayList<>())
                    .add(new ScenarioResult(
                            scenario.request(),
                            scenario.response(),
                            testClass + "." + testMethod,
                            failure.isEmpty() ? ScenarioStatus.PASSED : ScenarioStatus.FAILED,
                            failure.map(this::failureSummary).orElse("")
                    ));
        }

        @Override
        public void close() throws IOException {
            Files.createDirectories(HTML_REPORT_PATH.getParent());
            ReportData data = buildReportData();
            Files.writeString(HTML_REPORT_PATH, toHtml(data), StandardCharsets.UTF_8);
        }

        private void registerRoute(RequestMappingInfo info, HandlerMethod handlerMethod) {
            if (!handlerMethod.getBeanType().getPackageName().startsWith("com.dibyanshusinha.apiserv.service.")) {
                return;
            }

            Set<String> routes = extractRoutes(info).stream()
                    .filter(route -> route.startsWith("/api/"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (routes.isEmpty()) {
                return;
            }

            Set<String> methods = info.getMethodsCondition().getMethods().stream()
                    .map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (methods.isEmpty()) {
                methods = Set.of("ANY");
            }

            String service = serviceName(handlerMethod);
            for (String route : routes) {
                for (String method : methods) {
                    ApiRoute apiRoute = new ApiRoute(service, method, route);
                    discoveredRoutes.add(apiRoute);
                    registerExpectedResponses(apiRoute, handlerMethod);
                }
            }
        }

        private void registerExpectedResponses(ApiRoute route, HandlerMethod handlerMethod) {
            Set<ExpectedResponse> responses = new LinkedHashSet<>();
            ApiResponses apiResponses = handlerMethod.getMethodAnnotation(ApiResponses.class);
            if (apiResponses != null) {
                for (ApiResponse apiResponse : apiResponses.value()) {
                    toExpectedResponse(apiResponse).ifPresent(responses::add);
                }
            }

            ApiResponse apiResponse = handlerMethod.getMethodAnnotation(ApiResponse.class);
            if (apiResponse != null) {
                toExpectedResponse(apiResponse).ifPresent(responses::add);
            }

            if (!responses.isEmpty()) {
                expectedResponses.computeIfAbsent(route.key(), ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(responses);
            }
        }

        private Optional<ExpectedResponse> toExpectedResponse(ApiResponse apiResponse) {
            String responseCode = apiResponse.responseCode();
            if (responseCode == null || responseCode.isBlank() || responseCode.startsWith("5")) {
                return Optional.empty();
            }
            return Optional.of(new ExpectedResponse(responseCode, apiResponse.description()));
        }

        private Set<String> extractRoutes(RequestMappingInfo info) {
            if (info.getPathPatternsCondition() != null) {
                return info.getPathPatternsCondition().getPatternValues();
            }
            if (info.getPatternsCondition() != null) {
                return info.getPatternsCondition().getPatterns();
            }
            return Set.of();
        }

        private String serviceName(HandlerMethod handlerMethod) {
            String className = handlerMethod.getBeanType().getSimpleName();
            if (className.endsWith("Controller")) {
                className = className.substring(0, className.length() - "Controller".length());
            }
            return splitCamelCase(className);
        }

        private String splitCamelCase(String value) {
            return value.replaceAll("([a-z])([A-Z])", "$1 $2");
        }

        private ReportData buildReportData() {
            List<ApiRoute> routes = discoveredRoutes.stream()
                    .sorted(Comparator.comparing(ApiRoute::service).thenComparing(ApiRoute::route).thenComparing(ApiRoute::method))
                    .toList();

            Map<String, List<ApiRoute>> routesByService = routes.stream()
                    .collect(Collectors.groupingBy(ApiRoute::service, java.util.TreeMap::new, Collectors.toList()));

            List<ApiRoute> uncoveredRoutes = routes.stream()
                    .filter(route -> scenarioResults.getOrDefault(route.key(), List.of()).stream()
                            .noneMatch(ScenarioResult::passed))
                    .toList();

            int totalScenarios = scenarioResults.values().stream().mapToInt(List::size).sum();
            int missingResponses = expectedResponses.entrySet().stream()
                    .mapToInt(entry -> (int) entry.getValue().stream()
                            .filter(response -> !hasScenarioForResponse(entry.getKey(), response.responseCode()))
                            .count())
                    .sum();
            int failedScenarios = scenarioResults.values().stream()
                    .flatMap(List::stream)
                    .mapToInt(result -> result.failed() ? 1 : 0)
                    .sum();
            int coveredRoutes = routes.size() - uncoveredRoutes.size();

            return new ReportData(Instant.now(), routes, routesByService, uncoveredRoutes, coveredRoutes, totalScenarios, failedScenarios, missingResponses);
        }

        private boolean hasScenarioForResponse(String routeKey, String responseCode) {
            return scenarioResults.getOrDefault(routeKey, List.of()).stream()
                    .anyMatch(result -> result.response().startsWith(responseCode + " "));
        }

        private String toHtml(ReportData data) {
            int routeCount = data.routes().size();
            int coveredPercent = routeCount == 0 ? 100 : (int) Math.round((data.coveredRoutes() * 100.0) / routeCount);
            String statusClass = data.uncoveredRoutes().isEmpty() ? "covered" : "missed";
            int untestedRoutes = data.uncoveredRoutes().size();
            int passedScenarios = data.totalScenarios() - data.failedScenarios();
            String overallStatus = data.failedScenarios() == 0 && untestedRoutes == 0 && data.missingResponses() == 0 ? "PASSED" : "ATTENTION";
            String overallClass = "PASSED".equals(overallStatus) ? "passed" : "attention";

            StringBuilder html = new StringBuilder();
            html.append("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Integration API Scenario Coverage</title>
                      <style>
                        :root { --border: #d8dee4; --muted: #57606a; --bg: #f6f8fa; --ok: #1a7f37; --bad: #cf222e; --warn: #9a6700; }
                        * { box-sizing: border-box; }
                        body { margin: 0; background: #fff; color: #24292f; font-family: Arial, Helvetica, sans-serif; font-size: 14px; }
                        .breadcrumb { background: var(--bg); border-bottom: 1px solid var(--border); padding: 10px 18px; color: var(--muted); }
                        .breadcrumb strong { color: #24292f; }
                        main { padding: 22px 28px 32px; max-width: 1280px; margin: 0 auto; }
                        h1 { margin: 0; font-size: 24px; font-weight: 600; }
                        h2 { margin: 26px 0 12px; font-size: 18px; font-weight: 600; }
                        .meta { color: var(--muted); margin-top: 8px; line-height: 1.5; }
                        .hero { border: 1px solid var(--border); border-radius: 6px; padding: 18px; margin: 18px 0 20px; background: linear-gradient(180deg, #fff, #f8fafc); }
                        .hero-top { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; flex-wrap: wrap; }
                        .overall { font-size: 28px; font-weight: 700; line-height: 1; }
                        .overall.passed { color: var(--ok); }
                        .overall.attention { color: var(--bad); }
                        .summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; width: 100%; }
                        .metric { border: 1px solid var(--border); border-radius: 6px; padding: 12px; background: #fff; }
                        .metric-label { color: var(--muted); font-size: 12px; font-weight: 700; text-transform: uppercase; }
                        .metric-value { display: block; margin-top: 6px; font-size: 22px; font-weight: 700; }
                        .bar { height: 10px; border: 1px solid #c8c8c8; border-radius: 999px; background: #ffebe9; overflow: hidden; margin-top: 10px; min-width: 120px; }
                        .bar span { display: block; height: 100%; background: #3fb950; }
                        .covered, .passed { color: var(--ok); }
                        .missed, .failed { color: var(--bad); }
                        .attention { color: var(--warn); }
                        .tree { border: 1px solid var(--border); border-radius: 6px; margin-bottom: 18px; background: #fff; overflow: hidden; width: 100%; }
                        details.tree-node { border-top: 1px solid var(--border); }
                        details.tree-node:first-child { border-top: 0; }
                        details.tree-node[open] > summary.tree-summary { background: #f8fafc; }
                        summary.tree-summary { cursor: pointer; display: grid; grid-template-columns: 28px 1fr auto; gap: 10px; align-items: center; padding: 10px 14px; list-style: none; }
                        summary.tree-summary:hover { background: #f3f6f9; }
                        summary.tree-summary::-webkit-details-marker { display: none; }
                        summary.tree-summary::before { content: "›"; width: 22px; height: 22px; border: 1px solid var(--border); border-radius: 4px; color: var(--muted); background: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; line-height: 1; transition: transform 0.12s ease, background 0.12s ease; }
                        details[open] > summary.tree-summary::before { transform: rotate(90deg); background: #eef4ff; color: #0969da; }
                        .tree-label { display: inline-flex; gap: 8px; align-items: baseline; font-weight: 700; }
                        .tree-counts { color: var(--muted); font-size: 13px; }
                        .tree-children { margin-left: 22px; border-left: 1px solid var(--border); padding: 6px 10px 10px; }
                        details.route-card { border: 1px solid var(--border); border-radius: 6px; margin: 8px 0; background: #fff; overflow: hidden; }
                        details.route-card[open] { box-shadow: 0 1px 2px rgba(31, 35, 40, 0.08); }
                        summary.route-summary { cursor: pointer; display: grid; grid-template-columns: 28px 90px minmax(0, 1fr) 140px 150px; gap: 10px; align-items: center; padding: 10px 12px; list-style: none; }
                        summary.route-summary:hover { background: #f8fafc; }
                        summary.route-summary::-webkit-details-marker { display: none; }
                        summary.route-summary::before { content: "›"; width: 22px; height: 22px; border: 1px solid var(--border); border-radius: 4px; color: var(--muted); background: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; line-height: 1; transition: transform 0.12s ease, background 0.12s ease; }
                        details[open] summary.route-summary::before { transform: rotate(90deg); background: #eef4ff; color: #0969da; }
                        .method { font-family: Menlo, Consolas, monospace; font-weight: 700; }
                        .path { font-family: Menlo, Consolas, monospace; word-break: break-all; min-width: 0; }
                        .badge { display: inline-block; border-radius: 999px; padding: 3px 8px; background: #dafbe1; color: var(--ok); font-size: 12px; font-weight: 700; text-align: center; }
                        .badge.missing, .badge.failed { background: #ffebe9; color: var(--bad); }
                        .badge.warning { background: #fff8c5; color: var(--warn); }
                        .route-body { border-top: 1px solid var(--border); padding: 12px; background: #fbfcfd; }
                        .table-wrapper { width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; border: 1px solid var(--border); border-radius: 6px; }
                        .table-wrapper::-webkit-scrollbar { height: 6px; }
                        .table-wrapper::-webkit-scrollbar-track { background: #f6f8fa; border-radius: 3px; }
                        .table-wrapper::-webkit-scrollbar-thumb { background: #d0d7de; border-radius: 3px; }
                        .table-wrapper::-webkit-scrollbar-thumb:hover { background: #afb8c1; }
                        table.scenarios { border-collapse: collapse; width: 100%; min-width: 800px; background: #fff; }
                        table.scenarios th, table.scenarios td { border: 1px solid var(--border); padding: 8px 9px; vertical-align: top; }
                        table.scenarios th { background: var(--bg); text-align: left; font-size: 12px; text-transform: uppercase; color: var(--muted); }
                        .test, .failure { font-family: Menlo, Consolas, monospace; font-size: 12px; }
                        .failure { color: var(--bad); }
                        .empty { color: var(--muted); padding: 10px; }
                        .footer { border-top: 1px solid var(--border); color: var(--muted); padding: 10px 18px; text-align: right; background: var(--bg); }
                        @media (max-width: 820px) {
                          main { padding: 16px; }
                          .hero-top { display: block; }
                          summary.tree-summary { grid-template-columns: 28px 1fr; }
                          .tree-counts { grid-column: 2; }
                          .tree-children { margin-left: 12px; padding: 6px 4px 10px; }
                          summary.route-summary { grid-template-columns: 28px 1fr; gap: 6px; }
                          summary.route-summary span:not(:first-child) { grid-column: 2; }
                        }
                      </style>
                    </head>
                    <body>
                      <div class="breadcrumb"><strong>Integration API Scenario Coverage</strong></div>
                      <main>
                    """);

            html.append("<h1>Integration API Scenario Coverage</h1>\n");
            html.append("<div class=\"meta\">Generated by <code>mvn verify</code> at <code>")
                    .append(escape(data.generatedAt().toString()))
                    .append("</code>. Routes are discovered from Spring MVC mappings; scenarios come from integration tests annotated with <code>@ApiScenario</code>. Failed scenarios are reported here and still fail the Maven build.</div>\n");

            html.append("<section class=\"hero\"><div class=\"hero-top\"><div><div class=\"overall ")
                    .append(overallClass)
                    .append("\">")
                    .append(overallStatus)
                    .append("</div><div class=\"meta\">Integration route scenario execution result</div></div><div><span class=\"")
                    .append(statusClass)
                    .append("\">")
                    .append(coveredPercent)
                    .append("% route coverage</span><div class=\"bar\"><span style=\"width:")
                    .append(coveredPercent)
                    .append("%\"></span></div></div></div><div class=\"summary-grid\">")
                    .append(metric("Discovered routes", String.valueOf(routeCount), ""))
                    .append(metric("Covered routes", data.coveredRoutes() + " of " + routeCount, statusClass))
                    .append(metric("Scenarios", String.valueOf(data.totalScenarios()), ""))
                    .append(metric("Passed", String.valueOf(passedScenarios), "passed"))
                    .append(metric("Missing responses", String.valueOf(data.missingResponses()), data.missingResponses() == 0 ? "passed" : "attention"))
                    .append(metric("Failed / Untested", data.failedScenarios() + " / " + untestedRoutes, data.failedScenarios() == 0 && untestedRoutes == 0 ? "passed" : "failed"))
                    .append("</div></section>\n");

            data.routesByService().forEach((service, serviceRoutes) -> appendHtmlService(html, service, serviceRoutes));
            appendHtmlMissingRoutes(html, data.uncoveredRoutes());

            html.append("""
                      </main>
                      <div class="footer">Generated by API scenario coverage extension</div>
                    </body>
                    </html>
                    """);
            return html.toString();
        }

        private void appendHtmlService(StringBuilder html, String service, List<ApiRoute> serviceRoutes) {
            int serviceScenarios = serviceRoutes.stream()
                    .mapToInt(route -> scenarioResults.getOrDefault(route.key(), List.of()).size())
                    .sum();
            int serviceFailures = serviceRoutes.stream()
                    .flatMap(route -> scenarioResults.getOrDefault(route.key(), List.of()).stream())
                    .mapToInt(result -> result.failed() ? 1 : 0)
                    .sum();
            long serviceUntested = serviceRoutes.stream()
                    .filter(route -> scenarioResults.getOrDefault(route.key(), List.of()).stream().noneMatch(ScenarioResult::passed))
                    .count();
            int serviceMissingResponses = serviceRoutes.stream()
                    .mapToInt(route -> (int) expectedResponses.getOrDefault(route.key(), Set.of()).stream()
                            .filter(response -> !hasScenarioForResponse(route.key(), response.responseCode()))
                            .count())
                    .sum();
            Map<String, List<ApiRoute>> routesByVersion = serviceRoutes.stream()
                    .collect(Collectors.groupingBy(route -> apiVersion(route.route()), java.util.TreeMap::new, Collectors.toList()));

            html.append("<section class=\"tree\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">IntegrationTests</span><span class=\"tree-counts\">API calls: ")
                    .append(serviceRoutes.size())
                    .append(" | Scenarios: ")
                    .append(serviceScenarios)
                    .append(" | Failed: ")
                    .append(serviceFailures)
                        .append(" | Missing responses: ")
                        .append(serviceMissingResponses)
                        .append(" | Untested: ")
                        .append(serviceUntested)
                    .append("</span></summary><div class=\"tree-children\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">API</span><span class=\"tree-counts\">")
                    .append(serviceRoutes.size())
                    .append(" calls</span></summary><div class=\"tree-children\"><details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">")
                    .append(escape(service))
                    .append("</span><span class=\"tree-counts\">")
                    .append(serviceScenarios)
                    .append(" scenarios</span></summary><div class=\"tree-children\">\n");

            routesByVersion.forEach((version, routes) -> {
                html.append("<details class=\"tree-node\" open><summary class=\"tree-summary\"><span class=\"tree-label\">")
                        .append(escape(version))
                        .append("</span><span class=\"tree-counts\">")
                        .append(routes.size())
                        .append(" calls</span></summary><div class=\"tree-children\">\n");
                routes.forEach(route -> appendRouteCard(html, route));
                html.append("</div></details>\n");
            });

            html.append("</div></details></div></details></div></details></section>\n");
        }

        private String apiVersion(String route) {
            String[] parts = route.split("/");
            if (parts.length > 2 && "api".equals(parts[1])) {
                return parts[2];
            }
            return "unversioned";
        }

        private void appendRouteCard(StringBuilder html, ApiRoute route) {
            List<ScenarioResult> scenarios = scenarioResults.getOrDefault(route.key(), List.of()).stream()
                    .sorted(Comparator.comparing(ScenarioResult::request).thenComparing(ScenarioResult::response))
                    .toList();
            List<ExpectedResponse> missingResponses = expectedResponses.getOrDefault(route.key(), Set.of()).stream()
                    .filter(response -> !hasScenarioForResponse(route.key(), response.responseCode()))
                    .sorted(Comparator.comparing(ExpectedResponse::responseCode))
                    .toList();
            long failed = scenarios.stream().filter(ScenarioResult::failed).count();
            long passed = scenarios.stream().filter(ScenarioResult::passed).count();
            String badgeClass = scenarios.isEmpty() || passed == 0 ? "warning" : failed > 0 ? "failed" : !missingResponses.isEmpty() ? "missing" : "";
            String badgeText = scenarios.isEmpty() || passed == 0 ? "UNTESTED" : failed > 0 ? "FAILED" : !missingResponses.isEmpty() ? "MISSING" : "PASSED";

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
                    .append(" failed / ")
                    .append(missingResponses.size())
                    .append(" missing</span></summary><div class=\"route-body\">");

            if (scenarios.isEmpty() && missingResponses.isEmpty()) {
                html.append("<div class=\"empty\">No integration scenario is annotated for this route.</div>");
            } else {
                html.append("<div class=\"table-wrapper\"><table class=\"scenarios\"><thead><tr><th>Status</th><th>Request</th><th>Response</th><th>Test</th><th>Failure</th></tr></thead><tbody>");
                scenarios.forEach(scenario -> html.append("<tr><td><span class=\"badge")
                        .append(scenario.failed() ? " failed\">FAILED" : "\">PASSED")
                        .append("</span></td><td>")
                        .append(escape(scenario.request()))
                        .append("</td><td>")
                        .append(escape(scenario.response()))
                        .append("</td><td class=\"test\">")
                        .append(escape(scenario.test()))
                        .append("</td><td class=\"failure\">")
                        .append(escape(scenario.failure()))
                        .append("</td></tr>"));
                missingResponses.forEach(response -> html.append("<tr><td><span class=\"badge missing\">MISSING</span></td><td>Spec-defined response</td><td>")
                        .append(escape(response.responseCode()))
                        .append(" ")
                        .append(escape(response.description()))
                        .append("</td><td class=\"test\">No matching @ApiScenario</td><td class=\"failure\"></td></tr>"));
                html.append("</tbody></table></div>");
            }

            html.append("</div></details>\n");
        }

        private void appendHtmlMissingRoutes(StringBuilder html, List<ApiRoute> uncoveredRoutes) {
            html.append("<h2>Untested Route Coverage</h2>\n");
            if (uncoveredRoutes.isEmpty()) {
                html.append("<section class=\"tree\"><div class=\"empty\"><span class=\"badge\">PASSED</span> No discovered API routes are untested.</div></section>\n");
                return;
            }

            html.append("<section class=\"tree\"><div class=\"tree-children\">\n");
            uncoveredRoutes.forEach(route -> html.append("<div class=\"empty\"><span class=\"badge warning\">UNTESTED</span> ")
                    .append(escape(route.service()))
                    .append(" / <span class=\"method\">")
                    .append(escape(route.method()))
                    .append("</span> <span class=\"path\">")
                    .append(escape(route.route()))
                    .append("</span></div>\n"));
            html.append("</div></section>\n");
        }

        private String metric(String label, String value, String valueClass) {
            return "<div class=\"metric\"><span class=\"metric-label\">" + escape(label)
                    + "</span><span class=\"metric-value " + escape(valueClass) + "\">"
                    + escape(value) + "</span></div>";
        }

        private String escape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }

        private String failureSummary(Throwable failure) {
            String message = failure.getMessage();
            if (message == null || message.isBlank()) {
                return failure.getClass().getSimpleName();
            }
            return failure.getClass().getSimpleName() + ": " + message.lines().findFirst().orElse("");
        }

    }

    record ApiRoute(String service, String method, String route) {

        String key() {
            return method + " " + route;
        }
    }

    record ScenarioResult(String request, String response, String test, ScenarioStatus status, String failure) {

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

    record ExpectedResponse(String responseCode, String description) {
    }

    record ReportData(
            Instant generatedAt,
            List<ApiRoute> routes,
            Map<String, List<ApiRoute>> routesByService,
            List<ApiRoute> uncoveredRoutes,
            int coveredRoutes,
            int totalScenarios,
            int failedScenarios,
            int missingResponses
    ) {
    }
}
