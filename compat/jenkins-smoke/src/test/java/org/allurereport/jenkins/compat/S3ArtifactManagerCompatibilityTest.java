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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises report browsing through the real S3 Artifact Manager on a deterministic slow link.
 */
@Epic("Compatibility")
@Feature("Remote artifact storage")
@Tag("s3")
@EnabledIfSystemProperty(named = "compat.s3.enabled", matches = "true")
class S3ArtifactManagerCompatibilityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ALLURE_CLI_VERSION_PATTERN =
            Pattern.compile("<allureCommandline\\.version>([^<]+)</allureCommandline\\.version>");
    private static final Pattern URL_QUERY_PATTERN = Pattern.compile("(https?://[^\\s?]+)\\?[^\\s]+", Pattern.CASE_INSENSITIVE);

    private static final String ISSUE_URL = "https://github.com/jenkinsci/allure-plugin/issues/454";
    private static final String ARTIFACT_MANAGER_S3_VERSION = "962.v15f7000205fa_";
    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2025-09-07T16-13-09Z";
    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";
    private static final String PLUGIN_FILE_IN_IMAGE = "allure-jenkins-plugin.jpi";
    private static final String FIXTURE_FILE_IN_IMAGE = "s3-latency-result.json";
    private static final String JENKINS_INIT_FILE = "compat-init.groovy";
    private static final String S3_BUCKET = "allure-compat";
    private static final String S3_PREFIX = "compat/";
    private static final String S3_CREDENTIALS_ID = "minio-compat";
    private static final String MINIO_ALIAS = "minio";
    private static final String S3_PROXY_ALIAS = "s3-proxy";
    private static final String MINIO_USER = "minioadmin";
    private static final String MINIO_PASSWORD = "minioadmin";
    private static final String TARGET_ARCHIVE_ENTRY = "allure-report/index.html";

    private static final int MINIO_PORT = 9000;
    private static final int TOXIPROXY_CONTROL_PORT = 8474;
    private static final int TOXIPROXY_S3_PORT = 8666;
    private static final int GENERATED_ATTACHMENT_MEBIBYTES = 64;
    private static final int DOWNSTREAM_BANDWIDTH_KILOBYTES_PER_SECOND = 1024;
    private static final int DOWNSTREAM_LATENCY_MILLIS = 150;
    private static final long MINIMUM_BYTES_BEFORE_TARGET = 50L * 1024L * 1024L;

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READY_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REPORT_FETCH_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REPORT_LATENCY_BUDGET = Duration.ofSeconds(10);
    private static final long POLL_DELAY_MILLIS = 2_000L;

    private static final List<String> REQUIRED_PLUGINS = List.of(
            "bouncycastle-api:2.30.1.84-291.v9f17b_21896e2",
            "commons-lang3-api:3.20.0-109.ve43756e2d2b_4",
            "jackson2-api:2.22.2-445.vdc613f1d8012",
            "display-url-api:2.217.va_6b_de84cc74b_",
            "workflow-step-api:724.v538c2362b_dfb_",
            "script-security:1412.v7737b_3405f86",
            "structs:362.va_b_695ef4fdf9",
            "artifact-manager-s3:" + ARTIFACT_MANAGER_S3_VERSION
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Test
    @DisplayName("S3-backed report index stays responsive on a slow link")
    @Story("Remote archive random access")
    @Description("Stores a large Allure report through Jenkins' S3 Artifact Manager, constrains the MinIO "
            + "download link, and verifies that the first uncached index request does not stream the full archive.")
    void shouldServeLargeS3ReportWithinLatencyBudget() throws Exception {
        final Configuration config = Configuration.fromSystemProperties();
        final S3TestHarness harness = createTestHarness(config);
        final Path artifactRoot = config.artifactRoot();

        addTestParameters(config, harness);
        Allure.link("Remote ArtifactManager latency regression", ISSUE_URL);

        try (Network network = Network.newNetwork();
             GenericContainer<?> minio = createMinioContainer(network);
             GenericContainer<?> toxiproxy = createToxiproxyContainer(network);
             GenericContainer<?> jenkins = harness.createJenkinsContainer(network)) {
            try {
                Allure.step("Start MinIO and the S3 network proxy", () -> {
                    minio.start();
                    toxiproxy.start();
                    createS3Proxy(toxiproxy);
                });

                Allure.step("Start Jenkins with Allure and S3 Artifact Manager", jenkins::start);

                final String baseUrl = "http://" + jenkins.getHost() + ":" + jenkins.getMappedPort(8080) + "/";
                Allure.parameter("Jenkins base URL", baseUrl);
                writeAllureEnvironment(config, harness, baseUrl);

                Allure.step("Wait for the Jenkins script console", () -> waitForScriptConsole(baseUrl));

                final String setupScript = buildSetupScript(harness.allureCliVersion());
                final Path setupScriptPath = writeTextFile(
                        harness.generatedDir().resolve("s3-setup.groovy"),
                        setupScript
                );
                Allure.parameter("S3 setup script", setupScriptPath.toString());
                final JsonNode setup = Allure.step("Configure MinIO as Jenkins' artifact manager", () -> {
                    Allure.addAttachment("S3 Jenkins setup", "text/x-groovy", setupScript, ".groovy");
                    final JsonNode response = executeScriptForJson(baseUrl, setupScript, HTTP_TIMEOUT);
                    Allure.addAttachment(
                            "S3 Jenkins setup response",
                            "application/json",
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                            ".json"
                    );
                    return response;
                });
                assertEquals(ARTIFACT_MANAGER_S3_VERSION, setup.path("artifactManagerS3Version").asText());
                assertEquals(S3_BUCKET, setup.path("bucket").asText());
                assertEquals(S3_PROXY_ALIAS + ":" + TOXIPROXY_S3_PORT, setup.path("endpoint").asText());

                final JsonNode build = runS3Build(baseUrl, harness.generatedDir());
                assertEquals("SUCCESS", build.path("result").asText());
                assertTrue(build.path("archiveExists").asBoolean(), "The report archive must exist in MinIO");
                assertFalse(build.path("localArchiveExists").asBoolean(),
                        "The report archive must not be stored under the Jenkins build directory");
                assertTrue(build.path("archiveBytes").asLong() >= MINIMUM_BYTES_BEFORE_TARGET,
                        "The remote report archive must be at least 50 MiB");
                assertTrue(build.path("uncompressedBytesBeforeIndex").asLong() >= MINIMUM_BYTES_BEFORE_TARGET,
                        "The target index entry must follow at least 50 MiB of report data");

                final String proxyState = Allure.step("Constrain the S3 download link", () -> {
                    addSlowLinkToxics(toxiproxy);
                    final String state = getToxiproxyState(toxiproxy);
                    Allure.addAttachment("Toxiproxy S3 link state", "application/json", state, ".json");
                    return state;
                });

                final String reportUrl = joinUrl(
                        baseUrl,
                        build.path("buildUrl").asText()
                                + build.path("actionUrlName").asText()
                                + "/index.html"
                );
                Allure.link("S3-backed Allure report", reportUrl);

                final Instant startedAt = Instant.now();
                final HttpTextResponse report = Allure.step(
                        "Fetch the first uncached report index over the constrained S3 link",
                        () -> getText(reportUrl, REPORT_FETCH_TIMEOUT)
                );
                final Duration elapsed = Duration.between(startedAt, Instant.now());

                final Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("archiveBytes", build.path("archiveBytes").asLong());
                metrics.put("uncompressedBytesBeforeIndex", build.path("uncompressedBytesBeforeIndex").asLong());
                metrics.put("targetArchiveEntry", TARGET_ARCHIVE_ENTRY);
                metrics.put("downstreamBandwidthKilobytesPerSecond", DOWNSTREAM_BANDWIDTH_KILOBYTES_PER_SECOND);
                metrics.put("downstreamLatencyMillis", DOWNSTREAM_LATENCY_MILLIS);
                metrics.put("latencyBudgetMillis", REPORT_LATENCY_BUDGET.toMillis());
                metrics.put("observedLatencyMillis", elapsed.toMillis());
                metrics.put("statusCode", report.statusCode());
                metrics.put("cacheControl", report.cacheControl());
                metrics.put("toxiproxy", OBJECT_MAPPER.readTree(proxyState));
                Allure.addAttachment(
                        "S3 report latency metrics",
                        "application/json",
                        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(metrics),
                        ".json"
                );

                Allure.parameter("Remote archive bytes", build.path("archiveBytes").asLong());
                Allure.parameter("Bytes before index entry", build.path("uncompressedBytesBeforeIndex").asLong());
                Allure.parameter("Observed report latency (ms)", elapsed.toMillis());
                Allure.parameter("Report latency budget (ms)", REPORT_LATENCY_BUDGET.toMillis());

                assertEquals(200, report.statusCode());
                assertTrue(report.body().toLowerCase(Locale.ROOT).contains("<html"),
                        "The response must contain the Allure report index");
                assertEquals("private, max-age=31536000, immutable", report.cacheControl());
                assertTrue(elapsed.compareTo(REPORT_LATENCY_BUDGET) < 0,
                        () -> "Expected the S3-backed index in less than " + REPORT_LATENCY_BUDGET.toSeconds()
                                + " seconds, but it took " + elapsed.toMillis() + " ms");
            } finally {
                captureContainerEvidence(artifactRoot, "jenkins-s3", jenkins, true);
                captureContainerEvidence(artifactRoot, "minio", minio, false);
                captureContainerEvidence(artifactRoot, "toxiproxy", toxiproxy, false);
            }
        }
    }

    private JsonNode runS3Build(final String baseUrl, final Path generatedDir) throws Exception {
        final String script = buildS3CheckScript();
        final Path scriptPath = writeTextFile(generatedDir.resolve("s3-build-check.groovy"), script);
        Allure.parameter("S3 build check script", scriptPath.toString());

        return Allure.step("Publish the large report to MinIO and inspect its remote archive", () -> {
            Allure.addAttachment("S3 build check", "text/x-groovy", script, ".groovy");
            final JsonNode response = executeScriptForJson(baseUrl, script, BUILD_TIMEOUT);
            Allure.addAttachment(
                    "S3 build and archive response",
                    "application/json",
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response),
                    ".json"
            );
            return response;
        });
    }

    private void addTestParameters(final Configuration config, final S3TestHarness harness) {
        Allure.parameter("Jenkins requested version", config.requestedVersion());
        Allure.parameter("Jenkins Docker tag", config.normalizedImageTag());
        Allure.parameter("Plugin artifact", harness.pluginArtifact().toString());
        Allure.parameter("Allure CLI version", harness.allureCliVersion());
        Allure.parameter("S3 Artifact Manager version", ARTIFACT_MANAGER_S3_VERSION);
        Allure.parameter("MinIO image", MINIO_IMAGE);
        Allure.parameter("Toxiproxy image", TOXIPROXY_IMAGE);
        Allure.parameter("Generated attachment (MiB)", GENERATED_ATTACHMENT_MEBIBYTES);
        Allure.parameter("Downstream bandwidth (KiB/s)", DOWNSTREAM_BANDWIDTH_KILOBYTES_PER_SECOND);
        Allure.parameter("Downstream latency (ms)", DOWNSTREAM_LATENCY_MILLIS);
    }

    private GenericContainer<?> createMinioContainer(final Network network) {
        return new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(MINIO_ALIAS)
                .withExposedPorts(MINIO_PORT)
                .withEnv("MINIO_ROOT_USER", MINIO_USER)
                .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                .withCommand("server", "--console-address", ":9001", "/data")
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(MINIO_PORT))
                .withStartupTimeout(STARTUP_TIMEOUT);
    }

    private GenericContainer<?> createToxiproxyContainer(final Network network) {
        return new GenericContainer<>(DockerImageName.parse(TOXIPROXY_IMAGE))
                .withNetwork(network)
                .withNetworkAliases(S3_PROXY_ALIAS)
                .withExposedPorts(TOXIPROXY_CONTROL_PORT)
                .waitingFor(Wait.forHttp("/version").forPort(TOXIPROXY_CONTROL_PORT))
                .withStartupTimeout(STARTUP_TIMEOUT);
    }

    private void createS3Proxy(final GenericContainer<?> toxiproxy) throws Exception {
        postToxiproxyJson(toxiproxy, "/proxies", Map.of(
                "name", "minio",
                "listen", "0.0.0.0:" + TOXIPROXY_S3_PORT,
                "upstream", MINIO_ALIAS + ":" + MINIO_PORT
        ));
    }

    private void addSlowLinkToxics(final GenericContainer<?> toxiproxy) throws Exception {
        postToxiproxyJson(toxiproxy, "/proxies/minio/toxics", Map.of(
                "name", "s3-downstream-bandwidth",
                "type", "bandwidth",
                "stream", "downstream",
                "toxicity", 1.0,
                "attributes", Map.of("rate", DOWNSTREAM_BANDWIDTH_KILOBYTES_PER_SECOND)
        ));
        postToxiproxyJson(toxiproxy, "/proxies/minio/toxics", Map.of(
                "name", "s3-downstream-latency",
                "type", "latency",
                "stream", "downstream",
                "toxicity", 1.0,
                "attributes", Map.of("latency", DOWNSTREAM_LATENCY_MILLIS, "jitter", 0)
        ));
    }

    private void postToxiproxyJson(final GenericContainer<?> toxiproxy,
                                   final String path,
                                   final Map<String, ?> payload) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(toxiproxyUri(toxiproxy, path))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                .build();
        final HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Toxiproxy returned HTTP " + response.statusCode() + ": "
                    + response.body());
        }
    }

    private String getToxiproxyState(final GenericContainer<?> toxiproxy) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(toxiproxyUri(toxiproxy, "/proxies/minio"))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        final HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Toxiproxy returned HTTP " + response.statusCode() + ": "
                    + response.body());
        }
        return response.body();
    }

    private URI toxiproxyUri(final GenericContainer<?> toxiproxy, final String path) {
        return URI.create("http://" + toxiproxy.getHost() + ":"
                + toxiproxy.getMappedPort(TOXIPROXY_CONTROL_PORT) + path);
    }

    private void waitForScriptConsole(final String baseUrl) throws Exception {
        final Instant deadline = Instant.now().plus(READY_TIMEOUT);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                final String response = executeScript(baseUrl, "println('ready')", HTTP_TIMEOUT);
                if ("ready".equals(response.trim())) {
                    return;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
            Thread.sleep(POLL_DELAY_MILLIS);
        }
        throw new IllegalStateException("Timed out waiting for the Jenkins script console", lastFailure);
    }

    private JsonNode executeScriptForJson(final String baseUrl,
                                          final String script,
                                          final Duration timeout) throws Exception {
        final String response = executeScript(baseUrl, script, timeout).trim();
        try {
            return OBJECT_MAPPER.readTree(response);
        } catch (IOException exception) {
            throw new IllegalStateException("Expected JSON response from Jenkins but got:\n" + response, exception);
        }
    }

    private String executeScript(final String baseUrl,
                                 final String script,
                                 final Duration timeout) throws Exception {
        final String payload = "script=" + URLEncoder.encode(script, StandardCharsets.UTF_8);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(joinUrl(baseUrl, "scriptText")))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Jenkins script console returned HTTP " + response.statusCode()
                    + ":\n" + response.body());
        }
        return response.body();
    }

    private HttpTextResponse getText(final String url, final Duration timeout) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET()
                .build();
        final HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        return new HttpTextResponse(
                response.statusCode(),
                response.body(),
                response.headers().firstValue("Cache-Control").orElse("")
        );
    }

    private static S3TestHarness createTestHarness(final Configuration config) throws IOException {
        Files.createDirectories(config.artifactRoot());
        final Path generatedDir = Files.createDirectories(config.artifactRoot().resolve("generated-s3"));
        final Path pluginArtifact = locatePluginArtifact(config.rootDir());
        final Path fixture = config.rootDir().resolve(
                "compat/jenkins-smoke/src/test/resources/s3-latency-result.json"
        );
        assertFileExists(fixture, "S3 latency result fixture");
        final String allureCliVersion = resolveAllureCliVersion(config.rootDir());
        final Path pluginsTxt = writeTextFile(
                generatedDir.resolve("plugins.txt"),
                REQUIRED_PLUGINS.stream().collect(Collectors.joining(System.lineSeparator()))
                        + System.lineSeparator()
        );
        final Path initGroovy = writeTextFile(generatedDir.resolve(JENKINS_INIT_FILE), buildInitScript());
        final ImageFromDockerfile image = createJenkinsImage(
                config,
                pluginArtifact,
                fixture,
                pluginsTxt,
                initGroovy
        );
        return new S3TestHarness(config, generatedDir, pluginArtifact, allureCliVersion, image);
    }

    private static ImageFromDockerfile createJenkinsImage(final Configuration config,
                                                           final Path pluginArtifact,
                                                           final Path fixture,
                                                           final Path pluginsTxt,
                                                           final Path initGroovy) throws IOException {
        final String imageName = "allure-jenkins-s3-compat-"
                + Integer.toHexString(config.normalizedImageTag().hashCode()).toLowerCase(Locale.ROOT)
                + "-" + Long.toHexString(Files.getLastModifiedTime(pluginArtifact).toMillis());

        return new ImageFromDockerfile(imageName, false)
                .withFileFromPath("plugins.txt", pluginsTxt)
                .withFileFromPath(JENKINS_INIT_FILE, initGroovy)
                .withFileFromPath(FIXTURE_FILE_IN_IMAGE, fixture)
                .withFileFromPath(PLUGIN_FILE_IN_IMAGE, pluginArtifact)
                .withDockerfileFromBuilder(builder -> builder
                        .from("jenkins/jenkins:" + config.normalizedImageTag())
                        .user("root")
                        .run("mkdir -p /usr/share/jenkins/ref/plugins "
                                + "/usr/share/jenkins/ref/smoke-data /usr/share/jenkins/ref/init.groovy.d")
                        .copy("plugins.txt", "/usr/share/jenkins/ref/plugins.txt")
                        .run("chown -R jenkins:jenkins /usr/share/jenkins/ref")
                        .user("jenkins")
                        .run("jenkins-plugin-cli --latest=false --plugin-file /usr/share/jenkins/ref/plugins.txt")
                        .run("echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state")
                        .user("root")
                        .copy(JENKINS_INIT_FILE, "/usr/share/jenkins/ref/init.groovy.d/" + JENKINS_INIT_FILE)
                        .copy(FIXTURE_FILE_IN_IMAGE, "/usr/share/jenkins/ref/smoke-data/" + FIXTURE_FILE_IN_IMAGE)
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
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".hpi"))
                    .sorted()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No plugin .hpi artifact found in " + targetDir));
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

    private static String buildSetupScript(final String allureCliVersion) {
        return """
                import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl
                import com.cloudbees.plugins.credentials.CredentialsProvider
                import com.cloudbees.plugins.credentials.CredentialsScope
                import com.cloudbees.plugins.credentials.domains.Domain
                import groovy.json.JsonOutput
                import hudson.model.FreeStyleProject
                import hudson.tasks.Shell
                import hudson.tools.InstallSourceProperty
                import io.jenkins.plugins.artifact_manager_jclouds.JCloudsArtifactManagerFactory
                import io.jenkins.plugins.artifact_manager_jclouds.s3.S3BlobStore
                import io.jenkins.plugins.artifact_manager_jclouds.s3.S3BlobStoreConfig
                import io.jenkins.plugins.aws.global_configuration.CredentialsAwsGlobalConfiguration
                import jenkins.model.ArtifactManagerConfiguration
                import jenkins.model.Jenkins
                import org.allurereport.jenkins.AllureReportPublisher
                import org.allurereport.jenkins.config.ResultsConfig
                import org.allurereport.jenkins.tools.AllureCommandlineDirectInstaller
                import org.allurereport.jenkins.tools.AllureCommandlineInstallation

                def jenkins = Jenkins.get()
                def credentialsId = %s
                def minioUser = System.getenv('MINIO_ROOT_USER')
                def minioPassword = System.getenv('MINIO_ROOT_PASSWORD')
                def credentialsStore = CredentialsProvider.lookupStores(jenkins).iterator().next()
                credentialsStore.addCredentials(
                    Domain.global(),
                    new AWSCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        credentialsId,
                        minioUser,
                        minioPassword,
                        'MinIO compatibility credentials'
                    )
                )

                def awsConfig = CredentialsAwsGlobalConfiguration.get()
                awsConfig.setRegion('us-east-1')
                awsConfig.setCredentialsId(credentialsId)

                def s3Config = S3BlobStoreConfig.get()
                s3Config.setUseHttp(true)
                s3Config.setUsePathStyleUrl(true)
                s3Config.setDisableSessionToken(true)
                s3Config.setCustomEndpoint(%s)
                s3Config.setCustomSigningRegion('us-east-1')
                s3Config.setContainer(%s)
                s3Config.setPrefix(%s)
                s3Config.createS3Bucket(%s)

                ArtifactManagerConfiguration.get().artifactManagerFactories.replaceBy([
                    new JCloudsArtifactManagerFactory(new S3BlobStore())
                ])

                def allureVersion = %s
                def toolDescriptor = jenkins.getDescriptorByType(AllureCommandlineInstallation.DescriptorImpl.class)
                if (toolDescriptor.installations.length == 0) {
                    def installer = new AllureCommandlineDirectInstaller(allureVersion)
                    def installation = new AllureCommandlineInstallation(
                        'Allure ' + allureVersion,
                        '',
                        [new InstallSourceProperty([installer])]
                    )
                    toolDescriptor.setInstallations(installation)
                    toolDescriptor.save()
                }

                def job = jenkins.getItem('compat-s3-latency')
                if (!(job instanceof FreeStyleProject)) {
                    if (job != null) {
                        job.delete()
                    }
                    job = jenkins.createProject(FreeStyleProject, 'compat-s3-latency')
                }
                job.buildersList.clear()
                job.publishersList.clear()
                job.buildersList.add(new Shell(\"\"\"#!/bin/bash -e
                rm -rf allure-results
                mkdir -p allure-results
                cp /usr/share/jenkins/ref/smoke-data/%s allure-results/s3-latency-result.json
                dd if=/dev/urandom of=allure-results/large-attachment.bin bs=1M count=%d status=none
                \"\"\".stripIndent()))
                job.publishersList.add(new AllureReportPublisher(ResultsConfig.convertPaths(['allure-results'])))
                job.save()
                jenkins.save()

                def artifactManagerPlugin = jenkins.pluginManager.getPlugin('artifact-manager-s3')
                println(JsonOutput.toJson([
                    artifactManagerS3Version: artifactManagerPlugin?.version,
                    bucket: s3Config.container,
                    prefix: s3Config.prefix,
                    endpoint: s3Config.customEndpoint,
                    useHttp: s3Config.useHttp,
                    usePathStyleUrl: s3Config.usePathStyleUrl,
                    disableSessionToken: s3Config.disableSessionToken,
                    job: job.fullName
                ]))
                """.formatted(
                groovyStringLiteral(S3_CREDENTIALS_ID),
                groovyStringLiteral(S3_PROXY_ALIAS + ":" + TOXIPROXY_S3_PORT),
                groovyStringLiteral(S3_BUCKET),
                groovyStringLiteral(S3_PREFIX),
                groovyStringLiteral(S3_BUCKET),
                groovyStringLiteral(allureCliVersion),
                FIXTURE_FILE_IN_IMAGE,
                GENERATED_ATTACHMENT_MEBIBYTES
        );
    }

    private static String buildS3CheckScript() {
        return """
                import groovy.json.JsonOutput
                import java.util.zip.ZipInputStream
                import jenkins.model.Jenkins
                import org.allurereport.jenkins.AllureReportBuildAction

                def job = Jenkins.get().getItemByFullName('compat-s3-latency')
                if (job == null) {
                    throw new IllegalStateException('compat-s3-latency job not found')
                }
                def future = job.scheduleBuild2(0)
                if (future == null) {
                    throw new IllegalStateException('Failed to schedule compat-s3-latency')
                }
                def build = future.get()
                def action = build.getAction(AllureReportBuildAction)
                if (action == null) {
                    throw new IllegalStateException('Allure action is missing')
                }

                def manager = build.getArtifactManager()
                def archive = manager.root().child('allure-report.zip')
                def localArchive = new File(build.artifactsDir, 'allure-report.zip')
                if (!archive.exists()) {
                    throw new IllegalStateException('allure-report.zip is missing from the artifact manager')
                }

                long bytesBeforeIndex = 0
                boolean foundIndex = false
                def zip = new ZipInputStream(archive.open())
                try {
                    byte[] buffer = new byte[8192]
                    def entry
                    while ((entry = zip.nextEntry) != null) {
                        if (entry.name == %s) {
                            foundIndex = true
                            break
                        }
                        int read
                        while ((read = zip.read(buffer)) >= 0) {
                            bytesBeforeIndex += read
                        }
                    }
                } finally {
                    zip.close()
                }
                if (!foundIndex) {
                    throw new IllegalStateException('Target index entry is missing from allure-report.zip')
                }

                println(JsonOutput.toJson([
                    result: String.valueOf(build.result),
                    buildUrl: build.url,
                    actionUrlName: action.urlName,
                    artifactManagerClass: manager.class.name,
                    archiveExists: archive.exists(),
                    archiveBytes: archive.length(),
                    localArchiveExists: localArchive.exists(),
                    targetArchiveEntry: %s,
                    uncompressedBytesBeforeIndex: bytesBeforeIndex,
                    summary: [
                        passed: action.buildSummary.passedCount,
                        failed: action.buildSummary.failedCount,
                        broken: action.buildSummary.brokenCount,
                        skipped: action.buildSummary.skipCount,
                        unknown: action.buildSummary.unknownCount
                    ]
                ]))
                """.formatted(
                groovyStringLiteral(TARGET_ARCHIVE_ENTRY),
                groovyStringLiteral(TARGET_ARCHIVE_ENTRY)
        );
    }

    private static void writeAllureEnvironment(final Configuration config,
                                               final S3TestHarness harness,
                                               final String baseUrl) throws IOException {
        final Properties environment = new Properties();
        environment.setProperty("Jenkins requested version", config.requestedVersion());
        environment.setProperty("Jenkins Docker tag", config.normalizedImageTag());
        environment.setProperty("Jenkins base URL", baseUrl);
        environment.setProperty("Allure CLI version", harness.allureCliVersion());
        environment.setProperty("S3 Artifact Manager version", ARTIFACT_MANAGER_S3_VERSION);
        environment.setProperty("MinIO image", MINIO_IMAGE);
        environment.setProperty("Toxiproxy image", TOXIPROXY_IMAGE);

        final Path resultsDir = Files.createDirectories(config.artifactRoot().resolve("allure-results"));
        try (var writer = Files.newBufferedWriter(resultsDir.resolve("environment.properties"), StandardCharsets.UTF_8)) {
            environment.store(writer, "S3 compatibility test");
        }
    }

    private static void captureContainerEvidence(final Path artifactRoot,
                                                 final String name,
                                                 final GenericContainer<?> container,
                                                 final boolean redactUrls) {
        try {
            String logs = container.getLogs();
            logs = logs.replace(MINIO_USER, "<redacted access key>")
                    .replace(MINIO_PASSWORD, "<redacted secret key>");
            if (redactUrls) {
                logs = URL_QUERY_PATTERN.matcher(logs).replaceAll("$1?<redacted query>");
            }
            Allure.addAttachment(name + " container log", "text/plain", logs, ".log");
            writeTextFile(artifactRoot.resolve(name + ".log"), logs);
        } catch (RuntimeException | IOException ignored) {
            // A container that failed before creation has no logs to preserve.
        }
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
            final Path rootDir = Path.of(System.getProperty("compat.rootDir", "."))
                    .toAbsolutePath()
                    .normalize();
            final String requestedVersion = System.getProperty("compat.version", "2.541.3").trim();
            final String normalizedImageTag = normalizeJenkinsImageTag(requestedVersion);
            final Path artifactRoot = Path.of(System.getProperty(
                    "compat.artifactRoot",
                    rootDir.resolve("compat-artifacts")
                            .resolve(sanitizeFileName(requestedVersion) + "-s3")
                            .toString()
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

    private record S3TestHarness(Configuration config,
                                 Path generatedDir,
                                 Path pluginArtifact,
                                 String allureCliVersion,
                                 ImageFromDockerfile image) {

        private GenericContainer<?> createJenkinsContainer(final Network network) {
            return new GenericContainer<>(image)
                    .withNetwork(network)
                    .withNetworkAliases("jenkins")
                    .withExposedPorts(8080)
                    .withEnv("JAVA_OPTS", "-Djenkins.install.runSetupWizard=false")
                    .withEnv("MINIO_ROOT_USER", MINIO_USER)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                    .waitingFor(Wait.forLogMessage(".*Jenkins is fully up and running.*\\n", 1))
                    .withStartupTimeout(STARTUP_TIMEOUT);
        }
    }

    private record HttpTextResponse(int statusCode, String body, String cacheControl) {
    }
}
