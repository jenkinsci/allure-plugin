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
package org.allurereport.jenkins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.allurereport.jenkins.testdata.TestUtils;
import org.allurereport.jenkins.utils.AllureReportArchiveSource;
import org.allurereport.jenkins.utils.AllureReportArchiveSourceFactory;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.allurereport.jenkins.ArchivedReportTestSupport.buildArchivedReportWithEntries;
import static org.allurereport.jenkins.ArchivedReportTestSupport.buildDirectoryReportWithEntries;
import static org.allurereport.jenkins.ArchivedReportTestSupport.switchToRemoteArtifactManager;
import static org.allurereport.jenkins.testdata.TestUtils.createAllurePublisher;
import static org.allurereport.jenkins.testdata.TestUtils.getSimpleFileScm;
import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportBuildActionIT {

    private static final String RESULTS_DIR = "allure-results";
    private static final String REPORT_DIR = "allure-report";
    private static final String SLASH = "/";
    private static final String INDEX_FILE = "index.html";
    private static final String ALLURE_PATH = "allure/";
    private static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_ATTACHMENT = "attachment";
    private static final String INDEX_ENTRY = REPORT_DIR + SLASH + INDEX_FILE;
    private static final String INDEX_PATH = ALLURE_PATH + INDEX_FILE;
    private static final String ENCODED_ASSET_ENTRY = REPORT_DIR + "/data/space file.txt";
    private static final String ENCODED_ASSET_CONTENT = "decoded asset";
    private static final String HTML_ATTACHMENT_PATH = "data/attachments/foo.html";
    private static final String PLUGIN_ENTRYPOINT_PATH = "awesome/index.html";
    private static final String THIRD_PARTY_HTML_PATH = "third-party/custom/render.html";
    private static final String SVG_ATTACHMENT_PATH = "data/attachments/chart.svg";
    private static final String DOT_DOT_ASSET_PATH = "data/foo..bar.txt";
    private static final String DOT_DOT_ASSET_CONTENT = "dot characters asset";
    private static final String PARENT_DIRECTORY_REQUEST = "data%2F..%2F";
    private static final String TMP_REPORT_PATH = "foo@tmp/secret.txt";
    private static final String OUTSIDE_REPORT_FILE = "outside-report.txt";
    private static final String OUTSIDE_REPORT_CONTENT = "not an allure report file";
    private static final String JENKINS_CONFIG_XML = "config.xml";
    private static final String ARCHIVED_HTML_ARTIFACT_PATH = "archive/allure-results/foo.html";
    private static final String SCRIPT_ALERT = "<script>alert(1)</script>";
    private static final String EMPTY_INDEX_CONTENT = "<html/>";
    private static final String LEGACY_INDEX_CONTENT = "<html>legacy</html>";
    private static final String SVG_SCRIPT =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
    private static final String CSP_REPORT_POLICY = "";
    private static final String CSP_SANDBOX = "sandbox";
    private static final String CSP_ALLOW_SCRIPTS = "allow-scripts";
    private static final String CSP_ALLOW_SAME_ORIGIN = "allow-same-origin";
    private static final String CSP_BASE_URI_NONE = "base-uri 'none'";
    private static final String CSP_FORM_ACTION_NONE = "form-action 'none'";
    private static final String CSP_OBJECT_SRC_NONE = "object-src 'none'";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule jRule = new JenkinsRule();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();

    private static String commandline;
    private static String jdk;

    @BeforeClass
    public static void setUp() throws Exception {
        jdk = TestUtils.getJdk(jRule).getName();
        commandline = TestUtils.getAllureCommandline(jRule, folder).getName();
    }

    @Test
    public void shouldRenderBuildBadgeAndServeReportFromArchivedZip() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final HtmlPage buildPage = webClient.getPage(build);
        assertThat(buildPage.getByXPath("//a[contains(@href, '/allure')]")).isNotEmpty();
        assertThat(new File(build.getRootDir(), REPORT_DIR)).doesNotExist();

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + INDEX_PATH))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(readArchivedEntry(build, INDEX_ENTRY));
    }

    @Test
    public void shouldDownloadIndexFromArchivedZip() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + "downloadIndex"))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getResponseHeaderValue(HEADER_CONTENT_DISPOSITION))
                .contains("attachment; filename=\"index.html\"");
        assertThat(response.getContentAsString()).isEqualTo(readArchivedEntry(build, INDEX_ENTRY));
    }

    @Test
    public void shouldServeArchivedAssetWhenRequestPathIsUrlEncoded() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                ENCODED_ASSET_ENTRY, ENCODED_ASSET_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + "data/space%20file.txt"))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(ENCODED_ASSET_CONTENT);
    }

    @Test
    public void shouldServeArchivedAssetWhenFileNameContainsDotDotCharacters() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                REPORT_DIR + SLASH + DOT_DOT_ASSET_PATH, DOT_DOT_ASSET_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + DOT_DOT_ASSET_PATH))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(DOT_DOT_ASSET_CONTENT);
    }

    @Test
    public void shouldSandboxArchivedHtmlAttachmentWithoutForcingDownload() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                REPORT_DIR + SLASH + HTML_ATTACHMENT_PATH, SCRIPT_ALERT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + HTML_ATTACHMENT_PATH))
        );

        assertSandboxedActiveContentResponse(response, SCRIPT_ALERT);
    }

    @Test
    public void shouldKeepRelaxedCspForArchivedPluginEntrypointIndex() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                REPORT_DIR + SLASH + PLUGIN_ENTRYPOINT_PATH, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + PLUGIN_ENTRYPOINT_PATH))
        );

        assertRelaxedReportEntrypointResponse(response, LEGACY_INDEX_CONTENT);
    }

    @Test
    public void shouldSandboxArchivedThirdPartyHtmlDocumentWithoutForcingDownload() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                REPORT_DIR + SLASH + THIRD_PARTY_HTML_PATH, SCRIPT_ALERT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + THIRD_PARTY_HTML_PATH))
        );

        assertSandboxedActiveContentResponse(response, SCRIPT_ALERT);
    }

    @Test
    public void shouldRejectArchivedReportPathContainingParentDirectorySegment() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + PARENT_DIRECTORY_REQUEST + INDEX_FILE, 400);
    }

    @Test
    public void shouldHideArchivedReportTmpPath() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT,
                REPORT_DIR + SLASH + TMP_REPORT_PATH, OUTSIDE_REPORT_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + TMP_REPORT_PATH, 404);
    }

    @Test
    public void shouldServeReportFromArtifactManagerWhenLocalArchiveIsMissing() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final File remoteRoot = folder.newFolder();
        switchToRemoteArtifactManager(build, remoteRoot);

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + INDEX_PATH))
        );

        assertThat(new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP))
                .doesNotExist();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(readArchivedEntry(build, INDEX_ENTRY));
    }

    @Test
    public void shouldFallBackToDirectoryBackedReportWhenArchiveIsMissing() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + INDEX_PATH))
        );

        assertThat(new File(build.getArtifactsDir(), AllureReportArchiveSourceFactory.ALLURE_REPORT_ZIP))
                .doesNotExist();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(LEGACY_INDEX_CONTENT);
    }

    @Test
    public void shouldServeDirectoryBackedAssetWhenFileNameContainsDotDotCharacters() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                DOT_DOT_ASSET_PATH, DOT_DOT_ASSET_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + DOT_DOT_ASSET_PATH))
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(DOT_DOT_ASSET_CONTENT);
    }

    @Test
    public void shouldSandboxDirectoryBackedHtmlAttachmentWithoutForcingDownload() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                HTML_ATTACHMENT_PATH, SCRIPT_ALERT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + HTML_ATTACHMENT_PATH))
        );

        assertSandboxedActiveContentResponse(response, SCRIPT_ALERT);
    }

    @Test
    public void shouldKeepRelaxedCspForDirectoryBackedPluginEntrypointIndex() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                PLUGIN_ENTRYPOINT_PATH, EMPTY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + PLUGIN_ENTRYPOINT_PATH))
        );

        assertRelaxedReportEntrypointResponse(response, EMPTY_INDEX_CONTENT);
    }

    @Test
    public void shouldSandboxDirectoryBackedThirdPartyHtmlDocumentWithoutForcingDownload() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                THIRD_PARTY_HTML_PATH, SCRIPT_ALERT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + THIRD_PARTY_HTML_PATH))
        );

        assertSandboxedActiveContentResponse(response, SCRIPT_ALERT);
    }

    @Test
    public void shouldSandboxDirectoryBackedSvgAttachmentWithoutForcingDownload() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                SVG_ATTACHMENT_PATH, SVG_SCRIPT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse response = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), build.getUrl() + ALLURE_PATH + SVG_ATTACHMENT_PATH))
        );

        assertSandboxedActiveContentResponse(response, SVG_SCRIPT);
    }

    @Test
    public void shouldRejectDirectoryBackedReportPathContainingParentDirectorySegment() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + PARENT_DIRECTORY_REQUEST + INDEX_FILE, 400);
    }

    @Test
    public void shouldHideDirectoryBackedReportSymlinkPath() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final File outsideReport = new File(build.getRootDir(), OUTSIDE_REPORT_FILE);
        final Path link = new File(build.getRootDir(), REPORT_DIR + "/linked.txt").toPath();
        Files.writeString(outsideReport.toPath(), OUTSIDE_REPORT_CONTENT, StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(link, outsideReport.toPath());
        } catch (UnsupportedOperationException | IOException | SecurityException symlinkFailure) {
            Assume.assumeNoException(symlinkFailure);
        }

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        webClient.assertFails(build.getUrl() + ALLURE_PATH + "linked.txt", 404);
    }

    @Test
    public void shouldHideDirectoryBackedReportTmpPath() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT,
                TMP_REPORT_PATH, OUTSIDE_REPORT_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + TMP_REPORT_PATH, 404);
    }

    @Test
    public void shouldRejectPathTraversalRequests() throws Exception {
        final FreeStyleBuild build = buildSingleReportBuild();
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + "..;/secret.txt", 400);
    }

    @Test
    public void shouldRejectArchivedEncodedAbsolutePathToJenkinsConfigXml() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        final File configXml = new File(jRule.jenkins.getRootDir(), JENKINS_CONFIG_XML);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(configXml), 400);
    }

    @Test
    public void shouldRejectArchivedEncodedAbsolutePathToNonReportBuildFile() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final File outsideReport = new File(build.getRootDir(), OUTSIDE_REPORT_FILE);
        Files.writeString(outsideReport.toPath(), OUTSIDE_REPORT_CONTENT, StandardCharsets.UTF_8);

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(outsideReport), 400);
    }

    @Test
    public void shouldRejectArchivedEncodedAbsolutePathToArchivedHtmlArtifact() throws Exception {
        final FreeStyleBuild build = buildArchivedReportWithEntries(Map.of(
                INDEX_ENTRY, EMPTY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final File archivedHtml = new File(build.getRootDir(), ARCHIVED_HTML_ARTIFACT_PATH);
        Files.createDirectories(archivedHtml.toPath().getParent());
        Files.writeString(archivedHtml.toPath(), SCRIPT_ALERT, StandardCharsets.UTF_8);

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(archivedHtml), 400);
    }

    @Test
    public void shouldRejectDirectoryBackedEncodedAbsolutePathToJenkinsConfigXml() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        final File configXml = new File(jRule.jenkins.getRootDir(), JENKINS_CONFIG_XML);

        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(configXml), 400);
    }

    @Test
    public void shouldRejectDirectoryBackedEncodedAbsolutePathToNonReportBuildFile() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final File outsideReport = new File(build.getRootDir(), OUTSIDE_REPORT_FILE);
        Files.writeString(outsideReport.toPath(), OUTSIDE_REPORT_CONTENT, StandardCharsets.UTF_8);

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(outsideReport), 400);
    }

    @Test
    public void shouldRejectDirectoryBackedEncodedAbsolutePathToArchivedHtmlArtifact() throws Exception {
        final FreeStyleBuild build = buildDirectoryReportWithEntries(Map.of(
                INDEX_FILE, LEGACY_INDEX_CONTENT
        ), jRule, REPORT_DIR);
        final File archivedHtml = new File(build.getRootDir(), ARCHIVED_HTML_ARTIFACT_PATH);
        Files.createDirectories(archivedHtml.toPath().getParent());
        Files.writeString(archivedHtml.toPath(), SCRIPT_ALERT, StandardCharsets.UTF_8);

        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);
        webClient.assertFails(build.getUrl() + ALLURE_PATH + encodePath(archivedHtml), 400);
    }

    @Test
    public void shouldExposeGraphEndpointsAfterTwoReportBuilds() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));

        jRule.buildAndAssertSuccess(project);
        final FreeStyleBuild second = jRule.buildAndAssertSuccess(project);
        final JenkinsRule.WebClient webClient = jRule.createWebClient().withJavaScriptEnabled(false);

        final WebResponse graph = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), second.getUrl() + ALLURE_PATH + "graph"))
        );
        final WebResponse graphMap = webClient.loadWebResponse(
                new WebRequest(new URL(jRule.getURL(), second.getUrl() + ALLURE_PATH + "graphMap"))
        );

        assertThat(graph.getStatusCode()).isEqualTo(200);
        assertThat(graph.getContentType()).isEqualTo("image/png");
        assertThat(graphMap.getStatusCode()).isEqualTo(200);
        assertThat(graphMap.getContentAsString()).contains("<area");
    }

    private FreeStyleBuild buildSingleReportBuild() throws Exception {
        final FreeStyleProject project = createProject();
        project.getPublishersList().add(createAllurePublisher(jdk, commandline, RESULTS_DIR));
        return jRule.buildAndAssertSuccess(project);
    }

    private FreeStyleProject createProject() throws Exception {
        final FreeStyleProject project = jRule.createFreeStyleProject();
        project.setScm(getSimpleFileScm("sample-testsuite.xml", RESULTS_DIR + "/sample-testsuite.xml"));
        return project;
    }

    private String readArchivedEntry(final FreeStyleBuild build, final String entryPath) throws Exception {
        try (AllureReportArchiveSource source = AllureReportArchiveSourceFactory.forRun(build);
             InputStream inputStream = source.openEntry(entryPath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String encodePath(final File file) {
        return URLEncoder.encode(file.getAbsolutePath(), StandardCharsets.UTF_8);
    }

    private void assertSandboxedActiveContentResponse(final WebResponse response,
                                                      final String expectedContent) {
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(expectedContent);
        assertNoAttachmentContentDisposition(response);
        assertThat(response.getResponseHeaderValue(HEADER_CONTENT_SECURITY_POLICY))
                .contains(CSP_SANDBOX)
                .contains(CSP_ALLOW_SCRIPTS)
                .contains(CSP_BASE_URI_NONE)
                .contains(CSP_FORM_ACTION_NONE)
                .contains(CSP_OBJECT_SRC_NONE)
                .doesNotContain(CSP_ALLOW_SAME_ORIGIN);
    }

    private void assertRelaxedReportEntrypointResponse(final WebResponse response,
                                                      final String expectedContent) {
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo(expectedContent);
        assertThat(response.getResponseHeaderValue(HEADER_CONTENT_SECURITY_POLICY)).isEqualTo(CSP_REPORT_POLICY);
    }

    private void assertNoAttachmentContentDisposition(final WebResponse response) {
        final String contentDisposition = response.getResponseHeaderValue(HEADER_CONTENT_DISPOSITION);
        if (contentDisposition != null) {
            assertThat(contentDisposition.toLowerCase(Locale.ROOT)).doesNotStartWith(CONTENT_DISPOSITION_ATTACHMENT);
        }
    }
}
