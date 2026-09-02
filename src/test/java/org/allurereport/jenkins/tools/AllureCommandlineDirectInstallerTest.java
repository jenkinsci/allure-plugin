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
package org.allurereport.jenkins.tools;

import hudson.FilePath;
import hudson.util.StreamTaskListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AllureCommandlineDirectInstallerTest {

    private static final String VERSION_2_30_0 = "2.30.0";
    private static final String VERSION_2_35_1 = "2.35.1";
    private static final String MIRROR_URL = "https://my.mirror.example.com/maven2";
    private static final String NEXUS_URL =
            "https://nexus.corp.example.com/repository/maven-central";
    private static final String ARTIFACT_PATH_SUFFIX =
            "/io/qameta/allure/allure-commandline/2.30.0/allure-commandline-2.30.0.zip";
    private static final String EXPECTED_DEFAULT_URL =
            "https://repo1.maven.org/maven2" + ARTIFACT_PATH_SUFFIX;
    private static final String EXPECTED_MIRROR_URL =
            MIRROR_URL + ARTIFACT_PATH_SUFFIX;
    private static final String EXPECTED_VERSION_FRAGMENT =
            "/2.35.1/allure-commandline-2.35.1.zip";
    private static final String BIN_DIRECTORY = "bin";
    private static final String LIB_DIRECTORY = "lib";
    private static final String BIN_ALLURE = "bin/allure";
    private static final String LEGACY_VERSION_JAR = "lib/allure-commandline-2.30.0.jar";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void buildDownloadUrlUsesDefaultBaseUrl() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);

        final String url = installer.buildDownloadUrl(VERSION_2_30_0);

        assertThat(url).isEqualTo(EXPECTED_DEFAULT_URL);
    }

    @Test
    public void buildDownloadUrlUsesCustomBaseUrl() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);
        installer.setBaseUrl(MIRROR_URL);

        final String url = installer.buildDownloadUrl(VERSION_2_30_0);

        assertThat(url).isEqualTo(EXPECTED_MIRROR_URL);
    }

    @Test
    public void buildDownloadUrlStripsTrailingSlashFromBaseUrl() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);
        installer.setBaseUrl(MIRROR_URL + "///");

        final String url = installer.buildDownloadUrl(VERSION_2_30_0);

        assertThat(url).isEqualTo(EXPECTED_MIRROR_URL);
    }

    @Test
    public void buildDownloadUrlContainsCorrectVersion() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_35_1);

        final String url = installer.buildDownloadUrl(VERSION_2_35_1);

        assertThat(url).contains(EXPECTED_VERSION_FRAGMENT);
    }

    @Test
    public void effectiveBaseUrlReturnsDefaultWhenNull() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);

        assertThat(installer.effectiveBaseUrl())
                .isEqualTo(AllureCommandlineDirectInstaller.DEFAULT_BASE_URL);
    }

    @Test
    public void effectiveBaseUrlReturnsDefaultWhenBlank() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);
        installer.setBaseUrl("   ");

        assertThat(installer.effectiveBaseUrl())
                .isEqualTo(AllureCommandlineDirectInstaller.DEFAULT_BASE_URL);
    }

    @Test
    public void effectiveBaseUrlReturnsCustomValue() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);
        installer.setBaseUrl(NEXUS_URL);

        assertThat(installer.effectiveBaseUrl()).isEqualTo(NEXUS_URL);
    }

    @Test
    public void semverPatternMatchesValidVersions() {
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher(VERSION_2_30_0).matches()).isTrue();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher(VERSION_2_35_1).matches()).isTrue();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("10.0.0").matches()).isTrue();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("1.0.0-alpha").matches()).isTrue();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("1.0.0-alpha.1").matches()).isTrue();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("1.0.0-rc-feature.1").matches()).isTrue();
    }

    @Test
    public void semverPatternRejectsInvalidVersions() {
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("").matches()).isFalse();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("2.30").matches()).isFalse();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("latest").matches()).isFalse();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("v2.30.0").matches()).isFalse();
        assertThat(AllureCommandlineDirectInstaller.SEMVER_PATTERN.matcher("2.30.0.1").matches()).isFalse();
    }

    @Test
    public void directInstallerRejectsVersionPathTraversalBeforeBuildingPaths() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller("2.35.1/../../outside");

        assertThatThrownBy(installer::validatedVersion)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exact semantic version");
    }

    @Test
    public void directInstallerNormalizesAnExactVersion() throws Exception {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(" 2.35.1 ");

        assertThat(installer.validatedVersion()).isEqualTo(VERSION_2_35_1);
    }

    @Test
    public void zipSlipDetectsPathTraversal() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("../../../etc/passwd")).isTrue();
    }

    @Test
    public void zipSlipDetectsAbsolutePath() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("/etc/passwd")).isTrue();
    }

    @Test
    public void zipSlipAllowsNormalEntries() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry(BIN_ALLURE)).isFalse();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("lib/allure.jar")).isFalse();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("config/allure.yml")).isFalse();
    }

    @Test
    public void zipSlipAllowsEmbeddedDotDotInFilename() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("foo..bar/baz")).isFalse();
    }

    @Test
    public void zipSlipDetectsDotDotInMiddleOfPath() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("bin/../../../etc/passwd")).isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsStyleTraversal() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("bin\\..\\..\\etc\\passwd")).isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsDriveAbsolutePathBackslash() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("C:\\Windows\\System32\\drivers\\etc\\hosts"))
                .isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsDriveAbsolutePathSlash() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("C:/Windows/System32/drivers/etc/hosts"))
                .isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsUncAbsolutePath() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("\\\\server\\share\\file.txt"))
                .isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsRootRelativePath() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("\\Windows\\System32\\file.txt"))
                .isTrue();
    }

    @Test
    public void zipSlipDetectsWindowsTrailingSpaceAndPeriodAliases() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry(".. /outside.txt")).isTrue();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("../outside.txt")).isTrue();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("folder./outside.txt")).isTrue();
    }

    @Test
    public void zipSlipDetectsNtfsStreamsAndDeviceNames() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("bin/allure:payload")).isTrue();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("lib/CON.txt")).isTrue();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("lib/LPT1")).isTrue();
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("lib/COM¹.txt")).isTrue();
    }

    @Test
    public void extractionFailsBeforeWindowsAliasCanEscapeTarget() throws Exception {
        final Path archive = folder.newFile("malicious.zip").toPath();
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "allure-commandline-2.35.1/bin/allure", "fixture");
            writeEntry(zip, "allure-commandline-2.35.1/.. /outside.txt", "escaped");
        }
        final FilePath target = new FilePath(folder.newFolder("extract-parent")).child("target");
        final FilePath outside = target.getParent().child("outside.txt");
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_35_1);

        assertThatThrownBy(() -> installer.extractZip(
                new FilePath(archive.toFile()),
                target,
                VERSION_2_35_1
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Refusing unsafe archive entry");
        assertThat(outside.exists()).isFalse();
    }

    @Test
    public void extractionCountsPayloadHiddenInDirectoryEntries() throws Exception {
        final Path archive = folder.newFile("directory-payload.zip").toPath();
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "allure-commandline-2.35.1/lib/", "hidden payload");
        }
        final FilePath target = new FilePath(folder.newFolder("directory-payload-target"));
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_35_1);

        assertThatThrownBy(() -> installer.extractZip(
                new FilePath(archive.toFile()),
                target,
                VERSION_2_35_1,
                8,
                10
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exceeds the limit");
    }

    @Test
    public void managedDownloadRequiresHttpsAndRejectsEmbeddedCredentials() {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_35_1);
        installer.setRequireHttps(true);

        assertThatThrownBy(() -> installer.validateDownloadUrl(
                "http://mirror.example.test/allure-commandline.zip"
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("must use https");
        assertThatThrownBy(() -> installer.validateDownloadUrl(
                "https://user:secret@mirror.example.test/allure-commandline.zip"
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("must not contain credentials");
    }

    @Test
    public void mirrorUrlRedactionRemovesCredentialsQueryAndFragment() {
        assertThat(AllureCommandlineDirectInstaller.redactUrl(
                "https://user:secret@mirror.example.test/maven2?token=secret#fragment"
        )).isEqualTo("https://mirror.example.test/maven2");
    }

    @Test
    public void matchingLegacyCacheIsAdoptedWithoutDownload() throws Exception {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_30_0);
        final FilePath installation = new FilePath(folder.newFolder("matching-legacy"));
        installation.child(BIN_DIRECTORY).mkdirs();
        installation.child(LIB_DIRECTORY).mkdirs();
        installation.child(BIN_ALLURE).write("", StandardCharsets.UTF_8.name());
        installation.child(LEGACY_VERSION_JAR)
                .write("", StandardCharsets.UTF_8.name());

        final boolean cached = installer.isCachedInstallation(
                installation,
                VERSION_2_30_0,
                new StreamTaskListener(System.out, StandardCharsets.UTF_8)
        );

        assertThat(cached).isTrue();
        assertThat(installation.child(".allure-version").readToString().trim())
                .isEqualTo(VERSION_2_30_0);
    }

    @Test
    public void legacyCacheForDifferentVersionIsRejected() throws Exception {
        final AllureCommandlineDirectInstaller installer =
                new AllureCommandlineDirectInstaller(VERSION_2_35_1);
        final FilePath installation = new FilePath(folder.newFolder("stale-legacy"));
        installation.child(BIN_DIRECTORY).mkdirs();
        installation.child(LIB_DIRECTORY).mkdirs();
        installation.child(BIN_ALLURE).write("", StandardCharsets.UTF_8.name());
        installation.child(LEGACY_VERSION_JAR)
                .write("", StandardCharsets.UTF_8.name());

        final boolean cached = installer.isCachedInstallation(
                installation,
                VERSION_2_35_1,
                new StreamTaskListener(System.out, StandardCharsets.UTF_8)
        );

        assertThat(cached).isFalse();
        assertThat(installation.exists()).isFalse();
    }

    private static void writeEntry(final ZipOutputStream zip,
                                   final String name,
                                   final String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
