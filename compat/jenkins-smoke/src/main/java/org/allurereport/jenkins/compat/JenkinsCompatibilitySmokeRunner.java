/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.compat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Starts a real Jenkins in Docker, installs the plugin under test, configures smoke jobs,
 * and verifies the main plugin flows against a requested Jenkins version.
 */
public final class JenkinsCompatibilitySmokeRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ALLURE_CLI_VERSION_PATTERN =
            Pattern.compile("<allureCommandline\\.version>([^<]+)</allureCommandline\\.version>");

    private static final String SMOKE_DATA_DIR = "/usr/share/jenkins/ref/smoke-data";
    private static final String PASSED_SAMPLE_RESOURCE = "src/test/resources/sample-testsuite.xml";
    private static final String FAILED_SAMPLE_RESOURCE = "src/test/resources/sample-testsuite-with-failed.xml";
    private static final String PLUGIN_FILE_IN_IMAGE = "allure-jenkins-plugin.jpi";
    private static final String JENKINS_INIT_FILE = "compat-init.groovy";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_DELAY_MS = 5_000L;

    private static final List<String> REQUIRED_PLUGINS = List.of(
            "bouncycastle-api:2.30.1.78.1-248.ve27176eb_46cb_",
            "jackson2-api:2.17.0-389.va_5c7e45cd806",
            "display-url-api:2.204.vf6fddd8a_8b_e9",
            "matrix-project:839.vff91cd7e3a_b_2",
            "workflow-basic-steps:1058.vcb_fc1e3a_21a_9",
            "workflow-cps:4009.v0089238351a_9",
            "workflow-durable-task-step:1378.v6a_3e903058a_3",
            "workflow-job:1436.vfa_244484591f"
    );

    private final Configuration config;
    private final HttpClient httpClient;

    private JenkinsCompatibilitySmokeRunner(final Configuration config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void main(final String[] args) throws Exception {
        final Configuration config = Configuration.fromSystemProperties();
        new JenkinsCompatibilitySmokeRunner(config).run();
    }

    private void run() throws Exception {
        Files.createDirectories(config.artifactRoot());

        final Path generatedDir = Files.createDirectories(config.artifactRoot().resolve("generated"));
        final Path pluginArtifact = locatePluginArtifact(config.rootDir());
        final Path passedSample = config.rootDir().resolve(PASSED_SAMPLE_RESOURCE);
        final Path failedSample = config.rootDir().resolve(FAILED_SAMPLE_RESOURCE);
        assertFileExists(passedSample, "sample passed results");
        assertFileExists(failedSample, "sample failed results");

        final String allureCliVersion = resolveAllureCliVersion(config.rootDir());
        final Path pluginsTxt = writeTextFile(generatedDir.resolve("plugins.txt"), buildPluginsFile());
        final Path initGroovy = writeTextFile(generatedDir.resolve(JENKINS_INIT_FILE), buildInitScript());
        writeTextFile(config.artifactRoot().resolve("plugin-artifact.txt"), pluginArtifact.toString());
        writeTextFile(config.artifactRoot().resolve("docker-image-tag.txt"), config.normalizedImageTag());

        final ImageFromDockerfile image = createImage(pluginArtifact, passedSample, failedSample, pluginsTxt, initGroovy);
        final List<SmokeCheck> checks = buildChecks();
        final List<SmokeResult> results = new ArrayList<>();

        GenericContainer<?> jenkins = null;
        Throwable failure = null;
        String baseUrl = null;

        try {
            jenkins = new GenericContainer<>(image)
                    .withExposedPorts(8080)
                    .withEnv("JAVA_OPTS", "-Djenkins.install.runSetupWizard=false")
                    .waitingFor(Wait.forLogMessage(".*Jenkins is fully up and running.*\\n", 1))
                    .withStartupTimeout(STARTUP_TIMEOUT);

            jenkins.start();
            baseUrl = "http://" + jenkins.getHost() + ":" + jenkins.getMappedPort(8080) + "/";
            writeTextFile(config.artifactRoot().resolve("jenkins-base-url.txt"), baseUrl);

            waitForScriptConsole(baseUrl);

            final Path setupScriptPath = writeTextFile(
                    generatedDir.resolve("setup.groovy"),
                    buildSetupScript(allureCliVersion)
            );
            executeSetupScript(baseUrl, Files.readString(setupScriptPath, StandardCharsets.UTF_8));

            for (SmokeCheck check : checks) {
                results.add(runSmokeCheck(baseUrl, check, generatedDir));
            }
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            final String logs = jenkins == null ? "Jenkins container did not start." : jenkins.getLogs();
            writeTextFile(config.artifactRoot().resolve("jenkins.log"), logs);

            if (jenkins != null) {
                jenkins.stop();
            }

            writeJsonReport(results, pluginArtifact, allureCliVersion, failure);
            writeSummary(results, pluginArtifact, allureCliVersion, failure, baseUrl);
        }

        if (failure != null) {
            if (failure instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("Compatibility smoke failed", failure);
        }
    }

    private SmokeResult runSmokeCheck(final String baseUrl,
                                      final SmokeCheck check,
                                      final Path generatedDir) throws Exception {
        final Path scriptPath = writeTextFile(
                generatedDir.resolve(sanitizeFileName(check.jobName()) + ".groovy"),
                buildCheckScript(check)
        );
        final JsonNode node = executeScriptForJson(baseUrl, Files.readString(scriptPath, StandardCharsets.UTF_8));

        final JobSummary summary = new JobSummary(
                node.path("summary").path("passed").asInt(),
                node.path("summary").path("failed").asInt(),
                node.path("summary").path("broken").asInt(),
                node.path("summary").path("skipped").asInt(),
                node.path("summary").path("unknown").asInt()
        );

        final List<ChildBuildResult> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) {
            children.add(new ChildBuildResult(
                    child.path("url").asText(),
                    child.path("result").asText(),
                    child.path("actionUrlName").asText(),
                    new JobSummary(
                            child.path("summary").path("passed").asInt(),
                            child.path("summary").path("failed").asInt(),
                            child.path("summary").path("broken").asInt(),
                            child.path("summary").path("skipped").asInt(),
                            child.path("summary").path("unknown").asInt()
                    )
            ));
        }

        final String buildUrl = node.path("buildUrl").asText();
        final String actionUrlName = node.path("actionUrlName").asText();
        final String reportUrl = joinUrl(baseUrl, buildUrl + actionUrlName + "/");
        final String consoleUrl = joinUrl(baseUrl, buildUrl + "consoleText");

        final HttpTextResponse reportPage = getText(reportUrl);
        if (reportPage.statusCode() != 200) {
            throw new IllegalStateException("Expected report endpoint for " + check.jobName()
                    + " to return 200 but got " + reportPage.statusCode());
        }

        final HttpTextResponse consoleText = getText(consoleUrl);
        final String baseFileName = sanitizeFileName(check.jobName());
        writeTextFile(config.artifactRoot().resolve(baseFileName + "-report.html"), reportPage.body());
        writeTextFile(config.artifactRoot().resolve(baseFileName + "-console.log"), consoleText.body());

        return new SmokeResult(
                check.jobName(),
                node.path("result").asText(),
                buildUrl,
                reportUrl,
                reportPage.statusCode(),
                consoleUrl,
                summary,
                children
        );
    }

    private void waitForScriptConsole(final String baseUrl) throws Exception {
        final Instant deadline = Instant.now().plus(READY_TIMEOUT);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                final String response = executeScript(baseUrl, "println('ready')");
                if ("ready".equals(response.trim())) {
                    return;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
            sleepQuietly();
        }

        throw new IllegalStateException("Timed out waiting for Jenkins script console", lastFailure);
    }

    private void executeSetupScript(final String baseUrl, final String script) throws Exception {
        final String response = executeScript(baseUrl, script).trim();
        if (!"setup-complete".equals(response)) {
            throw new IllegalStateException("Unexpected setup response from Jenkins:\n" + response);
        }
    }

    private JsonNode executeScriptForJson(final String baseUrl, final String script) throws Exception {
        final String response = executeScript(baseUrl, script).trim();
        try {
            return OBJECT_MAPPER.readTree(response);
        } catch (IOException exception) {
            throw new IllegalStateException("Expected JSON response from Jenkins script but got:\n" + response,
                    exception);
        }
    }

    private String executeScript(final String baseUrl, final String script) throws Exception {
        final String payload = "script=" + URLEncoder.encode(script, StandardCharsets.UTF_8);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(joinUrl(baseUrl, "scriptText")))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        final HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Jenkins script console returned HTTP "
                    + response.statusCode() + ":\n" + response.body());
        }
        return response.body();
    }

    private HttpTextResponse getText(final String url) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        final HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpTextResponse(response.statusCode(), response.body());
    }

    private ImageFromDockerfile createImage(final Path pluginArtifact,
                                            final Path passedSample,
                                            final Path failedSample,
                                            final Path pluginsTxt,
                                            final Path initGroovy) {
        final String imageName = "allure-jenkins-compat-"
                + Integer.toHexString(config.normalizedImageTag().hashCode()).toLowerCase(Locale.ROOT);

        return new ImageFromDockerfile(imageName, false)
                .withFileFromPath("plugins.txt", pluginsTxt)
                .withFileFromPath(JENKINS_INIT_FILE, initGroovy)
                .withFileFromPath("sample-testsuite.xml", passedSample)
                .withFileFromPath("sample-testsuite-with-failed.xml", failedSample)
                .withFileFromPath(PLUGIN_FILE_IN_IMAGE, pluginArtifact)
                .withDockerfileFromBuilder(builder -> builder
                        .from("jenkins/jenkins:" + config.normalizedImageTag())
                        .user("root")
                        .run("mkdir -p /usr/share/jenkins/ref/plugins /usr/share/jenkins/ref/smoke-data /usr/share/jenkins/ref/init.groovy.d")
                        .copy("plugins.txt", "/usr/share/jenkins/ref/plugins.txt")
                        .run("chown -R jenkins:jenkins /usr/share/jenkins/ref")
                        .user("jenkins")
                        .run("jenkins-plugin-cli --latest=false --plugin-file /usr/share/jenkins/ref/plugins.txt")
                        .run("echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state")
                        .user("root")
                        .copy(JENKINS_INIT_FILE, "/usr/share/jenkins/ref/init.groovy.d/" + JENKINS_INIT_FILE)
                        .copy("sample-testsuite.xml", SMOKE_DATA_DIR + "/sample-testsuite.xml")
                        .copy("sample-testsuite-with-failed.xml", SMOKE_DATA_DIR + "/sample-testsuite-with-failed.xml")
                        .copy(PLUGIN_FILE_IN_IMAGE, "/usr/share/jenkins/ref/plugins/" + PLUGIN_FILE_IN_IMAGE)
                        .run("chown -R jenkins:jenkins /usr/share/jenkins/ref")
                        .user("jenkins")
                        .build());
    }

    private Path locatePluginArtifact(final Path rootDir) throws IOException {
        final Path targetDir = rootDir.resolve("target");
        if (Files.notExists(targetDir)) {
            throw new IllegalStateException("Plugin target directory does not exist: " + targetDir);
        }

        try (Stream<Path> stream = Files.list(targetDir)) {
            final List<Path> matches = stream
                    .filter(path -> path.getFileName().toString().endsWith(".hpi"))
                    .sorted()
                    .toList();
            if (matches.isEmpty()) {
                throw new IllegalStateException("No plugin .hpi artifact found in " + targetDir);
            }
            return matches.get(0);
        }
    }

    private String resolveAllureCliVersion(final Path rootDir) throws IOException {
        final String override = System.getProperty("compat.allureCliVersion");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }

        final String pom = Files.readString(rootDir.resolve("pom.xml"), StandardCharsets.UTF_8);
        final Matcher matcher = ALLURE_CLI_VERSION_PATTERN.matcher(pom);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        throw new IllegalStateException("Unable to resolve allureCommandline.version from root pom.xml");
    }

    private String buildPluginsFile() {
        return REQUIRED_PLUGINS.stream().collect(Collectors.joining(System.lineSeparator())) + System.lineSeparator();
    }

    private String buildInitScript() {
        return """
                import hudson.security.AuthorizationStrategy
                import hudson.security.SecurityRealm
                import jenkins.model.Jenkins

                def jenkins = Jenkins.get()
                jenkins.setNumExecutors(2)
                jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION)
                jenkins.setAuthorizationStrategy(new AuthorizationStrategy.Unsecured())
                jenkins.setCrumbIssuer(null)
                jenkins.save()
                """;
    }

    private String buildSetupScript(final String allureCliVersion) {
        final String allureVersionLiteral = groovyStringLiteral(allureCliVersion);
        final String passedSampleLiteral = groovyStringLiteral(SMOKE_DATA_DIR + "/sample-testsuite.xml");
        final String failedSampleLiteral = groovyStringLiteral(SMOKE_DATA_DIR + "/sample-testsuite-with-failed.xml");

        return """
                import hudson.matrix.Axis
                import hudson.matrix.MatrixProject
                import hudson.model.FreeStyleProject
                import hudson.tasks.Shell
                import hudson.tools.InstallSourceProperty
                import jenkins.model.Jenkins
                import org.allurereport.jenkins.AllureReportPublisher
                import org.allurereport.jenkins.config.ResultsConfig
                import org.allurereport.jenkins.tools.AllureCommandlineDirectInstaller
                import org.allurereport.jenkins.tools.AllureCommandlineInstallation
                import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
                import org.jenkinsci.plugins.workflow.job.WorkflowJob

                def jenkins = Jenkins.get()
                def allureVersion = %s
                def passedSample = %s
                def failedSample = %s
                def toolDescriptor = jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)

                if (toolDescriptor.installations.length == 0) {
                    def installer = new AllureCommandlineDirectInstaller(allureVersion)
                    def installation = new AllureCommandlineInstallation(
                        "Allure " + allureVersion,
                        "",
                        [new InstallSourceProperty([installer])]
                    )
                    toolDescriptor.setInstallations(installation)
                    toolDescriptor.save()
                }

                def createPublisher = { ->
                    new AllureReportPublisher(ResultsConfig.convertPaths(['allure-results']))
                }

                def ensureFreestyle = { String jobName, String dataFile, Integer failureThreshold ->
                    def job = jenkins.getItem(jobName)
                    if (!(job instanceof FreeStyleProject)) {
                        if (job != null) {
                            job.delete()
                        }
                        job = jenkins.createProject(FreeStyleProject, jobName)
                    }

                    job.buildersList.clear()
                    job.publishersList.clear()
                    job.buildersList.add(new Shell(\"\"\"#!/bin/bash -e
                    mkdir -p allure-results
                    cp ${dataFile} allure-results/${new File(dataFile).getName()}
                    \"\"\".stripIndent()))

                    def publisher = createPublisher()
                    if (failureThreshold != null) {
                        publisher.setFailureThresholdCount(failureThreshold)
                    }
                    job.publishersList.add(publisher)
                    job.save()
                }

                ensureFreestyle('compat-freestyle', passedSample, null)

                def pipeline = jenkins.getItem('compat-pipeline')
                if (!(pipeline instanceof WorkflowJob)) {
                    if (pipeline != null) {
                        pipeline.delete()
                    }
                    pipeline = jenkins.createProject(WorkflowJob, 'compat-pipeline')
                }
                pipeline.setDefinition(new CpsFlowDefinition(\"\"\"node {
                  sh '''#!/bin/bash -e
                  mkdir -p allure-results
                  cp %s allure-results/sample-testsuite.xml
                  '''
                  allure results: [[path: 'allure-results']]
                }\"\"\".stripIndent(), true))
                pipeline.save()

                def matrix = jenkins.getItem('compat-matrix')
                if (!(matrix instanceof MatrixProject)) {
                    if (matrix != null) {
                        matrix.delete()
                    }
                    matrix = jenkins.createProject(MatrixProject, 'compat-matrix')
                }
                matrix.axes.clear()
                matrix.axes.add(new Axis('items', 'first', 'second'))
                matrix.buildersList.clear()
                matrix.publishersList.clear()
                matrix.buildersList.add(new Shell(\"\"\"#!/bin/bash -e
                mkdir -p allure-results
                cp ${passedSample} allure-results/sample-testsuite.xml
                \"\"\".stripIndent()))
                matrix.publishersList.add(createPublisher())
                matrix.save()

                jenkins.save()
                println('setup-complete')
                """.formatted(
                allureVersionLiteral,
                passedSampleLiteral,
                failedSampleLiteral,
                passedSampleLiteral
        );
    }

    private String buildCheckScript(final SmokeCheck check) {
        if (check.matrix()) {
            return buildMatrixCheckScript(check);
        }
        return buildStandardCheckScript(check);
    }

    private String buildStandardCheckScript(final SmokeCheck check) {
        return """
                import groovy.json.JsonOutput
                import jenkins.model.Jenkins
                import org.allurereport.jenkins.AllureReportBuildAction

                def job = Jenkins.get().getItemByFullName(%s)
                if (job == null) {
                    throw new IllegalStateException("Job not found: " + %s)
                }

                def future = job.scheduleBuild2(0)
                if (future == null) {
                    throw new IllegalStateException("Failed to schedule build for " + job.fullName)
                }

                def build = future.get()
                def action = build.getAction(AllureReportBuildAction)
                if (action == null) {
                    throw new IllegalStateException("Allure action is missing for " + job.fullName)
                }

                def summary = action.getBuildSummary()
                def buildResult = String.valueOf(build.getResult())
                if (buildResult != %s) {
                    throw new IllegalStateException("Expected result %s but got " + buildResult)
                }
                if (summary.getPassedCount() != %d || summary.getFailedCount() != %d) {
                    throw new IllegalStateException("Unexpected summary for " + job.fullName + ": "
                        + "passed=" + summary.getPassedCount() + ", failed=" + summary.getFailedCount())
                }

                println(JsonOutput.toJson([
                    job: job.fullName,
                    result: buildResult,
                    buildUrl: build.getUrl(),
                    actionUrlName: action.getUrlName(),
                    summary: [
                        passed: summary.getPassedCount(),
                        failed: summary.getFailedCount(),
                        broken: summary.getBrokenCount(),
                        skipped: summary.getSkipCount(),
                        unknown: summary.getUnknownCount()
                    ],
                    children: []
                ]))
                """.formatted(
                groovyStringLiteral(check.jobName()),
                groovyStringLiteral(check.jobName()),
                groovyStringLiteral(check.expectedResult()),
                groovyStringLiteral(check.expectedResult()),
                check.expectedPassed(),
                check.expectedFailed()
        );
    }

    private String buildMatrixCheckScript(final SmokeCheck check) {
        return """
                import groovy.json.JsonOutput
                import jenkins.model.Jenkins
                import org.allurereport.jenkins.AllureReportBuildAction

                def job = Jenkins.get().getItemByFullName(%s)
                if (job == null) {
                    throw new IllegalStateException("Job not found: " + %s)
                }

                def future = job.scheduleBuild2(0)
                if (future == null) {
                    throw new IllegalStateException("Failed to schedule build for " + job.fullName)
                }

                def build = future.get()
                def action = build.getAction(AllureReportBuildAction)
                if (action == null) {
                    throw new IllegalStateException("Allure parent action is missing for " + job.fullName)
                }

                def summary = action.getBuildSummary()
                def buildResult = String.valueOf(build.getResult())
                if (buildResult != %s) {
                    throw new IllegalStateException("Expected result %s but got " + buildResult)
                }

                def runs = build.getRuns().toList().sort { a, b -> a.getUrl() <=> b.getUrl() }
                if (runs.size() != 2) {
                    throw new IllegalStateException("Expected 2 matrix runs but got " + runs.size())
                }

                def children = runs.collect { run ->
                    def childAction = run.getAction(AllureReportBuildAction)
                    if (childAction == null) {
                        throw new IllegalStateException("Child Allure action is missing for " + run.getUrl())
                    }
                    def childSummary = childAction.getBuildSummary()
                    def childResult = String.valueOf(run.getResult())
                    if (childResult != 'SUCCESS') {
                        throw new IllegalStateException("Expected matrix child success but got " + childResult)
                    }
                    if (childSummary.getPassedCount() != 1 || childSummary.getFailedCount() != 0) {
                        throw new IllegalStateException("Unexpected child summary for " + run.getUrl())
                    }
                    [
                        url: run.getUrl(),
                        result: childResult,
                        actionUrlName: childAction.getUrlName(),
                        summary: [
                            passed: childSummary.getPassedCount(),
                            failed: childSummary.getFailedCount(),
                            broken: childSummary.getBrokenCount(),
                            skipped: childSummary.getSkipCount(),
                            unknown: childSummary.getUnknownCount()
                        ]
                    ]
                }

                println(JsonOutput.toJson([
                    job: job.fullName,
                    result: buildResult,
                    buildUrl: build.getUrl(),
                    actionUrlName: action.getUrlName(),
                    summary: [
                        passed: summary.getPassedCount(),
                        failed: summary.getFailedCount(),
                        broken: summary.getBrokenCount(),
                        skipped: summary.getSkipCount(),
                        unknown: summary.getUnknownCount()
                    ],
                    children: children
                ]))
                """.formatted(
                groovyStringLiteral(check.jobName()),
                groovyStringLiteral(check.jobName()),
                groovyStringLiteral(check.expectedResult()),
                groovyStringLiteral(check.expectedResult())
        );
    }

    private List<SmokeCheck> buildChecks() {
        return List.of(
                new SmokeCheck("compat-freestyle", "SUCCESS", 1, 0, false),
                new SmokeCheck("compat-pipeline", "SUCCESS", 1, 0, false),
                new SmokeCheck("compat-matrix", "SUCCESS", 2, 0, true)
        );
    }

    private void writeJsonReport(final List<SmokeResult> results,
                                 final Path pluginArtifact,
                                 final String allureCliVersion,
                                 final Throwable failure) throws IOException {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestedVersion", config.requestedVersion());
        payload.put("jenkinsImageTag", config.normalizedImageTag());
        payload.put("pluginArtifact", pluginArtifact.toString());
        payload.put("allureCliVersion", allureCliVersion);
        payload.put("results", results);
        payload.put("failure", failure == null ? null : failure.toString());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(config.artifactRoot().resolve("results.json").toFile(), payload);
    }

    private void writeSummary(final List<SmokeResult> results,
                              final Path pluginArtifact,
                              final String allureCliVersion,
                              final Throwable failure,
                              final String baseUrl) throws IOException {
        final StringBuilder summary = new StringBuilder();
        summary.append("# Jenkins Compatibility Smoke").append(System.lineSeparator()).append(System.lineSeparator());
        summary.append("- Requested Jenkins version: `").append(config.requestedVersion()).append('`')
                .append(System.lineSeparator());
        summary.append("- Jenkins Docker tag: `").append(config.normalizedImageTag()).append('`')
                .append(System.lineSeparator());
        summary.append("- Plugin artifact: `").append(pluginArtifact).append('`')
                .append(System.lineSeparator());
        summary.append("- Allure CLI version: `").append(allureCliVersion).append('`')
                .append(System.lineSeparator());
        if (baseUrl != null) {
            summary.append("- Jenkins base URL: `").append(baseUrl).append('`')
                    .append(System.lineSeparator());
        }
        summary.append(System.lineSeparator());
        summary.append("| Job | Result | Report | Passed | Failed |").append(System.lineSeparator());
        summary.append("| --- | --- | --- | --- | --- |").append(System.lineSeparator());
        for (SmokeResult result : results) {
            summary.append("| ")
                    .append(result.jobName())
                    .append(" | ")
                    .append(result.result())
                    .append(" | ")
                    .append(result.reportStatusCode())
                    .append(" | ")
                    .append(result.summary().passed())
                    .append(" | ")
                    .append(result.summary().failed())
                    .append(" |")
                    .append(System.lineSeparator());
        }
        if (failure != null) {
            summary.append(System.lineSeparator())
                    .append("## Failure").append(System.lineSeparator()).append(System.lineSeparator())
                    .append("```").append(System.lineSeparator())
                    .append(failure).append(System.lineSeparator())
                    .append("```").append(System.lineSeparator());
        }

        writeTextFile(config.artifactRoot().resolve("summary.md"), summary.toString());
    }

    private Path writeTextFile(final Path path, final String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static void assertFileExists(final Path path, final String description) {
        if (Files.notExists(path)) {
            throw new IllegalStateException("Missing " + description + " file: " + path);
        }
    }

    private void sleepQuietly() throws InterruptedException {
        Thread.sleep(POLL_DELAY_MS);
    }

    private static String joinUrl(final String baseUrl, final String relative) {
        return baseUrl.endsWith("/") ? baseUrl + relative : baseUrl + "/" + relative;
    }

    private static String groovyStringLiteral(final String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String sanitizeFileName(final String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record Configuration(Path rootDir,
                                 String requestedVersion,
                                 String normalizedImageTag,
                                 Path artifactRoot) {

        private static Configuration fromSystemProperties() {
            final Path rootDir = Path.of(System.getProperty("compat.rootDir", ".")).toAbsolutePath().normalize();
            final String requestedVersion = System.getProperty("compat.version", "2.462.1").trim();
            final String normalizedImageTag = normalizeJenkinsImageTag(requestedVersion);
            final Path artifactRoot = Path.of(System.getProperty(
                    "compat.artifactRoot",
                    rootDir.resolve("compat-artifacts").resolve(sanitizeFileName(requestedVersion)).toString()
            )).toAbsolutePath().normalize();

            return new Configuration(rootDir, requestedVersion, normalizedImageTag, artifactRoot);
        }

        private static String normalizeJenkinsImageTag(final String requestedVersion) {
            if (requestedVersion.contains(":")) {
                throw new IllegalArgumentException("compat.version must be a Jenkins Docker tag or bare version, "
                        + "not a full image reference: " + requestedVersion);
            }
            if (requestedVersion.contains("jdk") || requestedVersion.contains("lts")
                    || requestedVersion.contains("alpine")) {
                return requestedVersion;
            }
            return requestedVersion + "-lts-jdk17";
        }
    }

    private record SmokeCheck(String jobName,
                              String expectedResult,
                              int expectedPassed,
                              int expectedFailed,
                              boolean matrix) {
    }

    private record JobSummary(int passed, int failed, int broken, int skipped, int unknown) {
    }

    private record ChildBuildResult(String url,
                                    String result,
                                    String actionUrlName,
                                    JobSummary summary) {
    }

    private record SmokeResult(String jobName,
                               String result,
                               String buildUrl,
                               String reportUrl,
                               int reportStatusCode,
                               String consoleUrl,
                               JobSummary summary,
                               List<ChildBuildResult> children) {
    }

    private record HttpTextResponse(int statusCode, String body) {
    }
}
