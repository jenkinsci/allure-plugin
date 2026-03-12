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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void zipSlipDetectsPathTraversal() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("../../../etc/passwd")).isTrue();
    }

    @Test
    public void zipSlipDetectsAbsolutePath() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("/etc/passwd")).isTrue();
    }

    @Test
    public void zipSlipAllowsNormalEntries() {
        assertThat(AllureCommandlineDirectInstaller.isUnsafeZipEntry("bin/allure")).isFalse();
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
}
