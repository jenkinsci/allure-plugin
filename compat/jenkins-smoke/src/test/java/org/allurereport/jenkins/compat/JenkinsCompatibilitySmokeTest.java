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
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Starts a real Jenkins in Docker, installs the plugin under test, configures smoke jobs,
 * and verifies the main plugin flows against a requested Jenkins version.
 */
@Epic("Compatibility")
@Feature("Jenkins smoke")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JenkinsCompatibilitySmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ALLURE_CLI_VERSION_PATTERN =
            Pattern.compile("<allureCommandline\\.version>([^<]+)</allureCommandline\\.version>");

    private static final String SMOKE_DATA_DIR = "/usr/share/jenkins/ref/smoke-data";
    private static final String PASSED_SAMPLE_RESOURCE = "src/test/resources/sample-testsuite.xml";
    private static final String PLUGIN_FILE_IN_IMAGE = "allure-jenkins-plugin.jpi";
    private static final String JENKINS_INIT_FILE = "compat-init.groovy";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SMOKE_CHECK_TIMEOUT = Duration.ofMinutes(10);
    private static final long POLL_DELAY_MS = 5_000L;

    private static final List<String> REQUIRED_PLUGINS = List.of(
            "bouncycastle-api:2.30.1.78.1-248.ve27176eb_46cb_",
            "commons-lang3-api:3.17.0-84.vb_b_938040b_078",
            "jackson2-api:2.17.0-389.va_5c7e45cd806",
            "display-url-api:2.204.vf6fddd8a_8b_e9",
            "matrix-project:839.vff91cd7e3a_b_2",
            "workflow-basic-steps:1058.vcb_fc1e3a_21a_9",
            "workflow-cps:4009.v0089238351a_9",
            "workflow-durable-task-step:1378.v6a_3e903058a_3",
            "workflow-job:1436.vfa_244484591f"
    );

    private static final TestHarness TEST_HARNESS = createTestHarness();

    @Container
    private static final GenericContainer<?> JENKINS = TEST_HARNESS.createContainer();

    private final Configuration config = TEST_HARNESS.config();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Path generatedDir = TEST_HARNESS.generatedDir();
    private final Path pluginArtifact = TEST_HARNESS.pluginArtifact();
    private final String allureCliVersion = TEST_HARNESS.allureCliVersion();
    private String baseUrl;

    @BeforeAll
    void setUp() throws Exception {
        writeTextFile(config.artifactRoot().resolve("plugin-artifact.txt"), pluginArtifact.toString());
        writeTextFile(config.artifactRoot().resolve("docker-image-tag.txt"), config.normalizedImageTag());
        baseUrl = "http://" + JENKINS.getHost() + ":" + JENKINS.getMappedPort(8080) + "/";
        writeTextFile(config.artifactRoot().resolve("jenkins-base-url.txt"), baseUrl);
        writeAllureEnvironment();

        waitForScriptConsole();

        final Path setupScriptPath = writeTextFile(
                generatedDir.resolve("setup.groovy"),
                buildSetupScript(allureCliVersion)
        );
        executeSetupScript(Files.readString(setupScriptPath, StandardCharsets.UTF_8));
    }

    @AfterAll
    void tearDown() throws Exception {
        final String logs = JENKINS.isRunning() ? JENKINS.getLogs() : "Jenkins container did not start.";
        final Path jenkinsLog = writeTextFile(config.artifactRoot().resolve("jenkins.log"), logs);
        writeGlobalAttachments(jenkinsLog);
    }

    @Test
    @DisplayName("Freestyle job generates Allure report")
    @Story("Freestyle report generation")
    @Description("Runs a freestyle Jenkins job and verifies that the plugin publishes an Allure report action.")
    void shouldGenerateFreestyleReport() throws Exception {
        runWithAllure(new SmokeCheck("compat-freestyle", "SUCCESS", 1, 0, false));
    }

    @Test
    @DisplayName("Pipeline job runs allure step")
    @Story("Pipeline step execution")
    @Description("Runs a Jenkins Pipeline that invokes the allure step and verifies the published report.")
    void shouldRunPipelineStep() throws Exception {
        runWithAllure(new SmokeCheck("compat-pipeline", "SUCCESS", 1, 0, false));
    }

    @Test
    @DisplayName("Matrix job generates parent and child Allure reports")
    @Story("Matrix aggregation")
    @Description("Runs a matrix Jenkins job and verifies the parent and child Allure reports are generated.")
    void shouldGenerateMatrixReports() throws Exception {
        runWithAllure(new SmokeCheck("compat-matrix", "SUCCESS", 2, 0, true));
    }

    private void runWithAllure(final SmokeCheck check) throws Exception {
        addAllureParameters(check);
        try {
            runSmokeCheck(check);
        } catch (Exception exception) {
            attachJenkinsControllerLog();
            throw exception;
        } catch (AssertionError error) {
            attachJenkinsControllerLog();
            throw error;
        }
    }

    private void runSmokeCheck(final SmokeCheck check) throws Exception {
        final String script = buildCheckScript(check);
        final Path scriptPath = writeTextFile(
                generatedDir.resolve(sanitizeFileName(check.jobName()) + ".groovy"),
                script
        );
        Allure.parameter("Groovy check script", scriptPath.toString());
        final JsonNode node = Allure.step("Run " + check.jobName() + " and verify its Allure summary", () -> {
            Allure.addAttachment(check.jobName() + " Groovy check", "text/x-groovy", script, ".groovy");
            final JsonNode response = executeScriptForJson(script);
            Allure.addAttachment(
                    check.jobName() + " Jenkins smoke response",
                    "application/json",
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                    ".json"
            );
            return response;
        });

        final String buildUrl = node.path("buildUrl").asText();
        final String actionUrlName = node.path("actionUrlName").asText();
        final String absoluteBuildUrl = joinUrl(baseUrl, buildUrl);
        final String reportUrl = joinUrl(baseUrl, buildUrl + actionUrlName + "/");
        final String consoleUrl = joinUrl(baseUrl, buildUrl + "consoleText");
        Allure.link(check.jobName() + " Jenkins build", absoluteBuildUrl);
        Allure.link(check.jobName() + " Allure report", reportUrl);
        addChildReportLinks(check, node);

        final HttpTextResponse reportPage = Allure.step(
                "Verify the published Allure report for " + check.jobName(),
                () -> {
                    final HttpTextResponse response = getText(reportUrl);
                    Allure.addAttachment(check.jobName() + " report page", "text/html", response.body(), ".html");
                    assertEquals(200, response.statusCode(),
                            () -> "Unexpected report endpoint status for " + check.jobName());
                    return response;
                }
        );
        addJenkinsResultParameters(node, reportUrl, consoleUrl, reportPage.statusCode());

        final HttpTextResponse consoleText = Allure.step(
                "Capture the Jenkins console for " + check.jobName(),
                () -> {
                    final HttpTextResponse response = getText(consoleUrl);
                    Allure.addAttachment(check.jobName() + " console", "text/plain", response.body(), ".log");
                    return response;
                }
        );
        final String baseFileName = sanitizeFileName(check.jobName());
        writeTextFile(config.artifactRoot().resolve(baseFileName + "-report.html"), reportPage.body());
        writeTextFile(config.artifactRoot().resolve(baseFileName + "-console.log"), consoleText.body());
    }

    private void addAllureParameters(final SmokeCheck check) {
        Allure.parameter("Jenkins requested version", config.requestedVersion());
        Allure.parameter("Jenkins Docker tag", config.normalizedImageTag());
        Allure.parameter("Jenkins base URL", baseUrl);
        Allure.parameter("Plugin artifact", pluginArtifact.toString());
        Allure.parameter("Allure CLI version", allureCliVersion);
        Allure.parameter("Smoke job", check.jobName());
        Allure.parameter("Expected build result", check.expectedResult());
        Allure.parameter("Expected passed tests", check.expectedPassed());
        Allure.parameter("Expected failed tests", check.expectedFailed());
    }

    private void addJenkinsResultParameters(final JsonNode node,
                                            final String reportUrl,
                                            final String consoleUrl,
                                            final int reportStatusCode) {
        Allure.parameter("Build result", node.path("result").asText());
        Allure.parameter("Report URL", reportUrl);
        Allure.parameter("Report HTTP status", reportStatusCode);
        Allure.parameter("Console URL", consoleUrl);
        Allure.parameter("Passed tests", node.path("summary").path("passed").asInt());
        Allure.parameter("Failed tests", node.path("summary").path("failed").asInt());
        Allure.parameter("Broken tests", node.path("summary").path("broken").asInt());
        Allure.parameter("Skipped tests", node.path("summary").path("skipped").asInt());
        Allure.parameter("Unknown tests", node.path("summary").path("unknown").asInt());
    }

    private void addChildReportLinks(final SmokeCheck check, final JsonNode node) {
        for (JsonNode child : node.path("children")) {
            final String childReportUrl = joinUrl(
                    baseUrl,
                    child.path("url").asText() + child.path("actionUrlName").asText() + "/"
            );
            Allure.link(check.jobName() + " child " + child.path("url").asText(), childReportUrl);
        }
    }

    private void attachJenkinsControllerLog() {
        if (JENKINS.isRunning()) {
            Allure.addAttachment("Jenkins controller log", "text/plain", JENKINS.getLogs(), ".log");
        }
    }

    private void waitForScriptConsole() throws Exception {
        final Instant deadline = Instant.now().plus(READY_TIMEOUT);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                final String response = executeScript("println('ready')");
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

    private void executeSetupScript(final String script) throws Exception {
        final String response = executeScript(script).trim();
        if (!"setup-complete".equals(response)) {
            throw new IllegalStateException("Unexpected setup response from Jenkins:\n" + response);
        }
    }

    private JsonNode executeScriptForJson(final String script) throws Exception {
        final String response = executeScript(script, SMOKE_CHECK_TIMEOUT).trim();
        try {
            return OBJECT_MAPPER.readTree(response);
        } catch (IOException exception) {
            throw new IllegalStateException("Expected JSON response from Jenkins script but got:\n" + response,
                    exception);
        }
    }

    private String executeScript(final String script) throws Exception {
        return executeScript(script, HTTP_TIMEOUT);
    }

    private String executeScript(final String script, final Duration timeout) throws Exception {
        final String payload = "script=" + URLEncoder.encode(script, StandardCharsets.UTF_8);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(joinUrl(baseUrl, "scriptText")))
                .timeout(timeout)
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

    private static TestHarness createTestHarness() {
        try {
            final Configuration config = Configuration.fromSystemProperties();
            Files.createDirectories(config.artifactRoot());
            final Path generatedDir = Files.createDirectories(config.artifactRoot().resolve("generated"));
            final Path pluginArtifact = locatePluginArtifact(config.rootDir());
            final Path passedSample = config.rootDir().resolve(PASSED_SAMPLE_RESOURCE);
            assertFileExists(passedSample, "sample passed results");
            final String allureCliVersion = resolveAllureCliVersion(config.rootDir());
            final Path pluginsTxt = writeTextFile(generatedDir.resolve("plugins.txt"), buildPluginsFile());
            final Path initGroovy = writeTextFile(generatedDir.resolve(JENKINS_INIT_FILE), buildInitScript());
            final ImageFromDockerfile image =
                    createImage(config, pluginArtifact, passedSample, pluginsTxt, initGroovy);

            return new TestHarness(config, generatedDir, pluginArtifact, allureCliVersion, image);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize Jenkins compatibility test harness", exception);
        }
    }

    private static ImageFromDockerfile createImage(final Configuration config,
                                                   final Path pluginArtifact,
                                                   final Path passedSample,
                                                   final Path pluginsTxt,
                                                   final Path initGroovy) {
        final String imageName = "allure-jenkins-compat-"
                + Integer.toHexString(config.normalizedImageTag().hashCode()).toLowerCase(Locale.ROOT);

        return new ImageFromDockerfile(imageName, false)
                .withFileFromPath("plugins.txt", pluginsTxt)
                .withFileFromPath(JENKINS_INIT_FILE, initGroovy)
                .withFileFromPath("sample-testsuite.xml", passedSample)
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
                        .copy(PLUGIN_FILE_IN_IMAGE, "/usr/share/jenkins/ref/plugins/" + PLUGIN_FILE_IN_IMAGE)
                        .run("chown -R jenkins:jenkins /usr/share/jenkins/ref")
                        .user("jenkins")
                        .build());
    }

    private static Path locatePluginArtifact(final Path rootDir) throws IOException {
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

    private static String resolveAllureCliVersion(final Path rootDir) throws IOException {
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

    private static String buildPluginsFile() {
        return REQUIRED_PLUGINS.stream().collect(Collectors.joining(System.lineSeparator())) + System.lineSeparator();
    }

    private static String buildInitScript() {
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
                sed -i \"s#sampleTestCase#sampleTestCase-\\$items#\" allure-results/sample-testsuite.xml
                \"\"\".stripIndent()))
                matrix.publishersList.add(createPublisher())
                matrix.save()

                jenkins.save()
                println('setup-complete')
                """.formatted(
                allureVersionLiteral,
                passedSampleLiteral,
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

                def expectedPassed = %d
                def expectedFailed = %d
                if (summary.getPassedCount() != expectedPassed || summary.getFailedCount() != expectedFailed) {
                    throw new IllegalStateException("Expected parent summary passed=" + expectedPassed
                        + ", failed=" + expectedFailed + " but got passed=" + summary.getPassedCount()
                        + ", failed=" + summary.getFailedCount())
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
                groovyStringLiteral(check.expectedResult()),
                check.expectedPassed(),
                check.expectedFailed()
        );
    }

    private void writeAllureEnvironment() throws IOException {
        final Properties environment = new Properties();
        environment.setProperty("Jenkins requested version", config.requestedVersion());
        environment.setProperty("Jenkins Docker tag", config.normalizedImageTag());
        environment.setProperty("Allure CLI version", allureCliVersion);
        environment.setProperty("Plugin artifact", pluginArtifact.toString());
        environment.setProperty("Jenkins base URL", baseUrl);

        final Path resultsDir = Files.createDirectories(config.artifactRoot().resolve("allure-results"));
        try (var writer = Files.newBufferedWriter(resultsDir.resolve("environment.properties"), StandardCharsets.UTF_8)) {
            environment.store(writer, "Jenkins compatibility smoke");
        }
    }

    private void writeGlobalAttachments(final Path jenkinsLog) throws IOException {
        final Path resultsDir = Files.createDirectories(config.artifactRoot().resolve("allure-results"));
        final List<Map<String, String>> attachments = new ArrayList<>();

        addGlobalAttachment(resultsDir, attachments, jenkinsLog, "jenkins.log", "text/plain");
        addGlobalAttachment(
                resultsDir,
                attachments,
                generatedDir.resolve(JENKINS_INIT_FILE),
                JENKINS_INIT_FILE,
                "text/x-groovy"
        );
        addGlobalAttachment(
                resultsDir,
                attachments,
                generatedDir.resolve("setup.groovy"),
                "setup.groovy",
                "text/x-groovy"
        );
        addGlobalAttachment(
                resultsDir,
                attachments,
                generatedDir.resolve("plugins.txt"),
                "plugins.txt",
                "text/plain"
        );

        final Map<String, Object> globals = Map.of(
                "attachments", attachments,
                "errors", List.of()
        );
        writeTextFile(
                resultsDir.resolve(UUID.randomUUID() + "-globals.json"),
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(globals)
        );
    }

    private static void addGlobalAttachment(final Path resultsDir,
                                            final List<Map<String, String>> attachments,
                                            final Path artifact,
                                            final String name,
                                            final String type) throws IOException {
        if (Files.notExists(artifact)) {
            return;
        }

        final String artifactFileName = artifact.getFileName().toString();
        final int extensionIndex = artifactFileName.lastIndexOf('.');
        final String extension = extensionIndex >= 0 ? artifactFileName.substring(extensionIndex) : "";
        final String source = UUID.randomUUID() + "-attachment" + extension;
        Files.copy(artifact, resultsDir.resolve(source));
        attachments.add(Map.of(
                "name", name,
                "type", type,
                "source", source
        ));
    }

    private static Path writeTextFile(final Path path, final String content) throws IOException {
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

    private record TestHarness(Configuration config,
                               Path generatedDir,
                               Path pluginArtifact,
                               String allureCliVersion,
                               ImageFromDockerfile image) {

        private GenericContainer<?> createContainer() {
            return new GenericContainer<>(image)
                    .withExposedPorts(8080)
                    .withEnv("JAVA_OPTS", "-Djenkins.install.runSetupWizard=false")
                    .waitingFor(Wait.forLogMessage(".*Jenkins is fully up and running.*\\n", 1))
                    .withStartupTimeout(STARTUP_TIMEOUT);
        }
    }

    private record HttpTextResponse(int statusCode, String body) {
    }
}
