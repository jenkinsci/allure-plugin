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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AllureManagedInstallerTest {

    private static final String FIXED_ALLURE_2 = "2.35.1";
    private static final String FIXED_ALLURE_3 = "3.15.0";
    private static final String RELEASES_PREFIX = "releases/";
    private static final String PRERELEASE = "3.17.0-rc-feature.1";
    private static final String ALLURE_DIRECTORY = "allure";
    private static final String NPM_INSTALL = "install";
    private static final String NPM_CI = "ci";
    private static final String PACKAGE_LOCK_ONLY = "--package-lock-only";
    private static final String IGNORE_SCRIPTS = "--ignore-scripts";
    private static final String NO_AUDIT = "--no-audit";
    private static final String NO_FUND = "--no-fund";
    private static final String PACKAGE_URL =
            "https://registry.npmjs.org/allure/-/allure-3.15.0.tgz";
    private static final String PACKAGE_INTEGRITY = "sha512-YWJjZA==";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void recommendedPolicyResolvesPluginRecommendation() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED
        );

        assertThat(installer.isRecommended()).isTrue();
        assertThat(installer.resolveVersion()).isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION);
        assertThat(installer.releaseId())
                .isEqualTo(AllureRuntimeManifest.releaseId(AllureRuntimeManifest.RECOMMENDED_ALLURE_VERSION));
    }

    @Test
    public void recommendedAllure2PolicyResolvesPluginRecommendation() throws Exception {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_RECOMMENDED_ALLURE_2
        );

        assertThat(installer.isRecommended()).isTrue();
        assertThat(installer.resolveVersion()).isEqualTo(AllureRuntimeManifest.RECOMMENDED_ALLURE_2_VERSION);
    }

    @Test
    public void fixedPolicyPinsAllure2Version() throws Exception {
        final AllureManagedInstaller installer = fixedInstaller(FIXED_ALLURE_2);

        assertThat(installer.isRecommended()).isFalse();
        assertThat(installer.resolveVersion()).isEqualTo(FIXED_ALLURE_2);
    }

    @Test
    public void fixedPolicyPinsAllure3Version() throws Exception {
        final AllureManagedInstaller installer = fixedInstaller(FIXED_ALLURE_3);

        assertThat(installer.resolveVersion()).isEqualTo(FIXED_ALLURE_3);
        assertThat(AllureManagedInstaller.executableRelativePath(FIXED_ALLURE_3, false))
                .isEqualTo(RELEASES_PREFIX + AllureRuntimeManifest.releaseId(FIXED_ALLURE_3) + "/bin/allure");
        assertThat(AllureManagedInstaller.executableRelativePath(FIXED_ALLURE_3, true))
                .isEqualTo(RELEASES_PREFIX + AllureRuntimeManifest.releaseId(FIXED_ALLURE_3) + "/bin/allure.cmd");
    }

    @Test
    public void windowsManagedCommandUsesNodeExecutableAndCliScript() {
        assertThat(AllureManagedInstaller.commandRelativePaths(FIXED_ALLURE_3, NodePlatform.WINDOWS_X64))
                .containsExactly(
                        RELEASES_PREFIX + AllureRuntimeManifest.releaseId(FIXED_ALLURE_3)
                                + "/node-v" + AllureRuntimeManifest.RECOMMENDED_NODE_VERSION
                                + "-win-x64/node.exe",
                        RELEASES_PREFIX + AllureRuntimeManifest.releaseId(FIXED_ALLURE_3)
                                + "/allure/node_modules/allure/cli.js"
                );
    }

    @Test
    public void exactPrereleaseWithHyphenIsAccepted() throws Exception {
        final AllureManagedInstaller installer = fixedInstaller(PRERELEASE);

        assertThat(installer.resolveVersion()).isEqualTo(PRERELEASE);
    }

    @Test
    public void floatingVersionIsRejected() {
        final AllureManagedInstaller installer = fixedInstaller("latest");

        assertThatThrownBy(installer::resolveVersion)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("exact semantic version");
    }

    @Test
    public void unsupportedMajorIsRejected() {
        final AllureManagedInstaller installer = fixedInstaller("4.0.0");

        assertThatThrownBy(installer::resolveVersion)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("supports Allure 2.x and 3.x");
    }

    @Test
    public void unknownPolicyIsRejected() {
        final AllureManagedInstaller installer = new AllureManagedInstaller("recommended");
        installer.setVersion(FIXED_ALLURE_3);

        assertThatThrownBy(installer::resolveVersion)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Unknown Allure version policy");
    }

    @Test
    public void npmRegistryRequiresHttps() {
        final AllureManagedInstaller installer = fixedInstaller(FIXED_ALLURE_3);
        installer.setNpmRegistry("http://registry.example.test");

        assertThatThrownBy(installer::validatedNpmRegistry)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    public void fixedAllurePackageJsonPinsExactDependency() {
        assertThat(AllureManagedInstaller.packageJson(FIXED_ALLURE_3))
                .contains("\"dependencies\":{\"allure\":\"" + FIXED_ALLURE_3 + "\"}");
    }

    @Test
    public void dependencyResolutionCreatesLockfileWithoutInstallingPackages() throws Exception {
        final FilePath staging = new FilePath(folder.newFolder("staging"));
        final FilePath allureHome = staging.child(ALLURE_DIRECTORY);
        final FilePath npmCache = new FilePath(folder.newFolder("npm-cache"));

        final List<String> command = AllureManagedInstaller.npmCommand(
                staging,
                allureHome,
                npmCache,
                NodePlatform.LINUX_X64,
                AllureManagedInstaller.DEFAULT_NPM_REGISTRY,
                NPM_INSTALL,
                true
        );

        assertThat(command)
                .contains(NPM_INSTALL, PACKAGE_LOCK_ONLY, IGNORE_SCRIPTS, NO_AUDIT, NO_FUND)
                .doesNotContain("--no-package-lock", "--no-save");
    }

    @Test
    public void npmCiConsumesTheValidatedLockfileWithSafeFlags() throws Exception {
        final FilePath staging = new FilePath(folder.newFolder("ci-staging"));
        final FilePath allureHome = staging.child(ALLURE_DIRECTORY);
        final FilePath npmCache = new FilePath(folder.newFolder("ci-npm-cache"));

        final List<String> command = AllureManagedInstaller.npmCommand(
                staging,
                allureHome,
                npmCache,
                NodePlatform.LINUX_X64,
                AllureManagedInstaller.DEFAULT_NPM_REGISTRY,
                NPM_CI,
                false
        );

        assertThat(command)
                .contains(NPM_CI, IGNORE_SCRIPTS, "--omit=dev", NO_AUDIT, NO_FUND)
                .doesNotContain(PACKAGE_LOCK_ONLY, NPM_INSTALL);
    }

    @Test
    public void packageLockMustPinRequestedAllureVersion() throws Exception {
        final FilePath packageLock = new FilePath(folder.newFile("package-lock.json"));
        packageLock.write(
                packageLock(FIXED_ALLURE_3, "3.14.0", PACKAGE_URL, PACKAGE_INTEGRITY),
                StandardCharsets.UTF_8.name()
        );

        assertThatThrownBy(() -> AllureManagedInstaller.validatePackageLock(packageLock, FIXED_ALLURE_3))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("pins Allure 3.14.0 instead of " + FIXED_ALLURE_3);
    }

    @Test
    public void packageLockAcceptsExactVersionHttpsSourceAndSha512Integrity() throws Exception {
        final FilePath packageLock = new FilePath(folder.newFile("valid-package-lock.json"));
        packageLock.write(
                packageLock(FIXED_ALLURE_3, FIXED_ALLURE_3, PACKAGE_URL, PACKAGE_INTEGRITY),
                StandardCharsets.UTF_8.name()
        );

        assertThatCode(() -> AllureManagedInstaller.validatePackageLock(packageLock, FIXED_ALLURE_3))
                .doesNotThrowAnyException();
    }

    @Test
    public void packageLockRejectsInsecurePackageSource() throws Exception {
        final FilePath packageLock = new FilePath(folder.newFile("http-package-lock.json"));
        packageLock.write(
                packageLock(
                        FIXED_ALLURE_3,
                        FIXED_ALLURE_3,
                        "http://registry.example.test/allure.tgz",
                        PACKAGE_INTEGRITY
                ),
                StandardCharsets.UTF_8.name()
        );

        assertThatThrownBy(() -> AllureManagedInstaller.validatePackageLock(packageLock, FIXED_ALLURE_3))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("package URLs must use https");
    }

    @Test
    public void packageLockRejectsPackageWithoutSha512Integrity() throws Exception {
        final FilePath packageLock = new FilePath(folder.newFile("weak-package-lock.json"));
        packageLock.write(
                packageLock(FIXED_ALLURE_3, FIXED_ALLURE_3, PACKAGE_URL, "sha1-YWJjZA=="),
                StandardCharsets.UTF_8.name()
        );

        assertThatThrownBy(() -> AllureManagedInstaller.validatePackageLock(packageLock, FIXED_ALLURE_3))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("without SHA-512 integrity");
    }

    @Test
    public void processOutputIsDrainedButOnlyTheBoundedTailIsRetained() throws Exception {
        final byte[] discarded = new byte[5_000];
        final byte[] retained = new byte[20_000];
        Arrays.fill(discarded, (byte) 'a');
        Arrays.fill(retained, (byte) 'b');
        final byte[] output = new byte[discarded.length + retained.length];
        System.arraycopy(discarded, 0, output, 0, discarded.length);
        System.arraycopy(retained, 0, output, discarded.length, retained.length);

        final String captured = AllureManagedInstaller.readProcessOutput(
                new ByteArrayInputStream(output)
        );

        assertThat(captured).hasSize(retained.length);
        assertThat(captured.chars().allMatch(value -> value == 'b')).isTrue();
    }

    @Test
    public void nodeDownloadRequiresExactArchiveSize() throws Exception {
        final Path source = folder.newFile("node.zip").toPath();
        final byte[] content = "node-archive".getBytes(StandardCharsets.UTF_8);
        Files.write(source, content);
        final Path destination = folder.getRoot().toPath().resolve("downloaded.zip");

        AllureManagedInstaller.download(source.toUri().toURL(), destination, content.length);

        assertThat(Files.readAllBytes(destination)).containsExactly(content);
    }

    @Test
    public void nodeDownloadRejectsUnexpectedArchiveSize() throws Exception {
        final Path source = folder.newFile("oversized-node.zip").toPath();
        Files.writeString(source, "oversized", StandardCharsets.UTF_8);
        final Path destination = folder.getRoot().toPath().resolve("rejected.zip");

        assertThatThrownBy(() -> AllureManagedInstaller.download(
                source.toUri().toURL(),
                destination,
                3
        )).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("unexpected Content-Length");
    }

    private AllureManagedInstaller fixedInstaller(final String version) {
        final AllureManagedInstaller installer = new AllureManagedInstaller(
                AllureManagedInstaller.VERSION_POLICY_FIXED
        );
        installer.setVersion(version);
        return installer;
    }

    private static String packageLock(final String rootVersion,
                                      final String installedVersion,
                                      final String resolved,
                                      final String integrity) {
        return "{\"lockfileVersion\":3,\"packages\":{"
                + "\"\":{\"dependencies\":{\"allure\":\"" + rootVersion + "\"}},"
                + "\"node_modules/allure\":{\"version\":\"" + installedVersion
                + "\",\"resolved\":\"" + resolved
                + "\",\"integrity\":\"" + integrity + "\"}}}";
    }
}
